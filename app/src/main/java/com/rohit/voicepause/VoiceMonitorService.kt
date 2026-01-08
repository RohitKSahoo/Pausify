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

    // ===== STATE FLAGS =====
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
        Log.i(TAG, "Service STARTED")

        Settings.setServiceRunning(applicationContext, true)
        startForeground(NOTIFICATION_ID, createNotification("Listening for voice"))

        lastMusicActiveTime = System.currentTimeMillis()

        if (intent?.action == ACTION_STOP_SERVICE) {
            Log.w(TAG, "Stop requested from notification")
            stopServiceCompletely("Manual stop")
            return START_NOT_STICKY
        }

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

        Log.i(
            TAG,
            "Initializing VAD | silenceDelay=${silenceDelayMs}ms"
        )

        val ok = vadProcessor.initialize(
            listener = this,
            silenceDelayMs = silenceDelayMs
        )

        if (!ok) {
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

                Log.v(
                    TAG,
                    "Monitor | musicPlaying=$musicPlaying pausedByVoice=$pausedByVoice"
                )

                if (musicPlaying || pausedByVoice) {
                    // Music playing OR paused by us → keep VAD alive
                    musicEverPlayed = true
                    lastMusicActiveTime = now

                    if (!vadProcessor.isRunning()) {
                        Log.i(TAG, "Starting VAD (music active or paused-by-voice)")
                        vadProcessor.start()
                    }
                } else {
                    // Music genuinely stopped
                    if (vadProcessor.isRunning()) {
                        Log.i(TAG, "Stopping VAD (music stopped)")
                        vadProcessor.stop()
                    }

                    if (musicEverPlayed) {
                        val idle = now - lastMusicActiveTime
                        Log.v(TAG, "Music idle for ${idle}ms")

                        if (idle >= AUTO_STOP_TIMEOUT_MS) {
                            stopServiceCompletely("Auto-stop: music idle")
                            return
                        }
                    }
                }

                // Sync silence delay dynamically (slider support)
                val newDelay = Settings.getSilenceDuration(applicationContext)
                if (newDelay != vadProcessor.getSilenceDelay()) {
                    Log.i(TAG, "Updating silence delay → ${newDelay}ms")
                    vadProcessor.setSilenceDelay(newDelay)
                }

                handler.postDelayed(this, MONITOR_INTERVAL_MS)

            } catch (e: Exception) {
                Log.e(TAG, "Monitor loop CRASH", e)
                stopServiceCompletely("Monitor crash: ${e.message}")
            }
        }
    }

    // ======================
    // AUDIO FOCUS
    // ======================

    private fun pauseMusic() {
        if (pausedByVoice) {
            Log.v(TAG, "pauseMusic ignored (already paused)")
            return
        }

        Log.i(TAG, "Requesting audio focus to PAUSE music")

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener { }
            .build()

        val result = audioManager.requestAudioFocus(request)

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
            pausedByVoice = true
            Log.i(TAG, "Music PAUSED by VoicePause")
            updateNotification("Voice detected – Music paused")
        } else {
            Log.w(TAG, "Audio focus request DENIED")
        }
    }

    private fun resumeMusic() {
        if (!pausedByVoice) {
            Log.v(TAG, "resumeMusic ignored (not paused by voice)")
            return
        }

        Log.i(TAG, "Resuming music (releasing audio focus)")
        abandonAudioFocus()
        pausedByVoice = false
        updateNotification("Listening for voice")
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
            Log.i(TAG, "Audio focus RELEASED")
        }
    }

    // ======================
    // VAD CALLBACKS
    // ======================

    override fun onSpeechDetected() {
        Log.i(TAG, "🔥 SPEECH DETECTED (callback)")
        pauseMusic()
    }

    override fun onSpeechEnded() {
        Log.i(TAG, "🟢 SPEECH ENDED (callback)")
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
        stopForeground(true)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
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
            .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(text))
    }
}
