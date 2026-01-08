package com.rohit.voicepause

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rohit.voicepause.audio.VadProcessor

class VoiceMonitorService : Service(), VadProcessor.VadProcessorListener {

    companion object {
        private const val TAG = "VoicePause/Service"

        const val ACTION_SERVICE_STARTED = "com.rohit.voicepause.ACTION_SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED = "com.rohit.voicepause.ACTION_SERVICE_STOPPED"
        private const val ACTION_STOP_SERVICE = "com.rohit.voicepause.ACTION_STOP_SERVICE"

        private const val NOTIFICATION_ID = 100
        private const val AUTO_STOP_TIMEOUT_MS = 60_000L
        private const val MONITOR_INTERVAL_MS = 1000L
    }

    // ===== SYSTEM =====
    private lateinit var audioManager: AudioManager
    private lateinit var vadProcessor: VadProcessor

    // ===== AUDIO FOCUS =====
    private var audioFocusRequest: AudioFocusRequest? = null

    // ===== STATE =====
    private var pausedByVoice = false
    private var musicEverPlayed = false
    private var lastMusicActiveTime = 0L

    // ===== THREADING =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    // ======================
    // SERVICE LIFECYCLE
    // ======================

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service CREATED")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vadProcessor = VadProcessor()

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // 🔴 Handle STOP FIRST
        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.i(TAG, "Stop requested from notification")
            stopServiceCompletely("Manual stop")
            return START_NOT_STICKY
        }

        Log.i(TAG, "Service STARTING")

        Settings.setServiceRunning(applicationContext, true)
        sendBroadcast(Intent(ACTION_SERVICE_STARTED))

        startForeground(
            NOTIFICATION_ID,
            createNotification("Listening for voice")
        )

        lastMusicActiveTime = System.currentTimeMillis()

        initializeVad()
        handler.post(musicMonitorLoop)

        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service DESTROYED")

        handler.removeCallbacksAndMessages(null)
        vadProcessor.release()
        abandonAudioFocus()
        workerThread.quitSafely()

        Settings.setServiceRunning(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ======================
    // VAD INIT
    // ======================

    private fun initializeVad() {
        val silenceDelayMs = Settings.getSilenceDuration(applicationContext)

        Log.i(TAG, "Initializing VAD | silenceDelay=${silenceDelayMs}ms")

        if (!vadProcessor.initialize(this, silenceDelayMs)) {
            Log.e(TAG, "VAD INIT FAILED")
            stopServiceCompletely("VAD init failed")
        }
    }

    // ======================
    // MUSIC MONITOR LOOP
    // ======================

    private val musicMonitorLoop = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                val musicPlaying = audioManager.isMusicActive

                Log.v(TAG, "Monitor | music=$musicPlaying pausedByVoice=$pausedByVoice")

                if (musicPlaying || pausedByVoice) {
                    musicEverPlayed = true
                    lastMusicActiveTime = now

                    if (!vadProcessor.isRunning()) {
                        Log.i(TAG, "Starting VAD")
                        vadProcessor.start()
                    }
                } else {
                    if (vadProcessor.isRunning()) {
                        Log.i(TAG, "Stopping VAD (music stopped)")
                        vadProcessor.stop()
                    }

                    if (musicEverPlayed) {
                        val idle = now - lastMusicActiveTime
                        if (idle >= AUTO_STOP_TIMEOUT_MS) {
                            stopServiceCompletely("Auto-stop: music idle")
                            return
                        }
                    }
                }

                val newDelay = Settings.getSilenceDuration(applicationContext)
                if (newDelay != vadProcessor.getSilenceDelay()) {
                    Log.i(TAG, "Updating silence delay → ${newDelay}ms")
                    vadProcessor.setSilenceDelay(newDelay)
                }

                handler.postDelayed(this, MONITOR_INTERVAL_MS)

            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop crash", e)
                stopServiceCompletely("Monitor crash")
            }
        }
    }

    // ======================
    // AUDIO FOCUS
    // ======================

    private fun pauseMusic() {
        if (pausedByVoice) return

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { }
            .build()

        if (audioManager.requestAudioFocus(request)
            == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        ) {
            audioFocusRequest = request
            pausedByVoice = true
            updateNotification("Voice detected – Music paused")
            Log.i(TAG, "Music PAUSED")
        }
    }

    private fun resumeMusic() {
        if (!pausedByVoice) return

        abandonAudioFocus()
        pausedByVoice = false
        updateNotification("Listening for voice")
        Log.i(TAG, "Music RESUMED")
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    // ======================
    // VAD CALLBACKS
    // ======================

    override fun onSpeechDetected() {
        Log.i(TAG, "🔥 SPEECH DETECTED")
        pauseMusic()
    }

    override fun onSpeechEnded() {
        Log.i(TAG, "🟢 SPEECH ENDED")
        resumeMusic()
    }

    override fun onVadError(error: String) {
        Log.e(TAG, "VAD ERROR: $error")
        stopServiceCompletely("VAD error")
    }

    // ======================
    // SERVICE STOP
    // ======================

    private fun stopServiceCompletely(reason: String) {
        Log.w(TAG, "SERVICE STOPPING → $reason")

        handler.removeCallbacksAndMessages(null)
        vadProcessor.stop()
        abandonAudioFocus()

        Settings.setServiceRunning(applicationContext, false)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))

        stopForeground(true)
        stopSelf()
    }

    // ======================
    // NOTIFICATION
    // ======================

    private fun createNotification(text: String): Notification {
        val channelId = "voice_pause_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VoicePause",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, VoiceMonitorService::class.java)
            .setAction(ACTION_STOP_SERVICE)

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoicePause Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent) // tap = stop
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, createNotification(text))
    }
}
