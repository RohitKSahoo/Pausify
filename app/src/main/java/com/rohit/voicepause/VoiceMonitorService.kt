package com.rohit.voicepause

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rohit.voicepause.audio.AudioProfile
import com.rohit.voicepause.audio.VadProcessor

class VoiceMonitorService : Service(), VadProcessor.VadProcessorListener {

    companion object {
        private const val TAG = "VoicePause/Service"

        const val ACTION_SERVICE_STARTED =
            "com.rohit.voicepause.ACTION_SERVICE_STARTED"
        const val ACTION_SERVICE_STOPPED =
            "com.rohit.voicepause.ACTION_SERVICE_STOPPED"
        private const val ACTION_STOP_SERVICE =
            "com.rohit.voicepause.ACTION_STOP_SERVICE"

        private const val NOTIFICATION_ID = 100
        private const val AUTO_STOP_TIMEOUT_MS = 60_000L
        private const val MONITOR_INTERVAL_MS = 1000L
    }

    // ===== SYSTEM =====
    private lateinit var audioManager: AudioManager
    private lateinit var vadProcessor: VadProcessor

    // ===== PROFILE =====
    private var currentProfile: AudioProfile = AudioProfile.BUSY

    // ===== AUDIO FOCUS =====
    private var audioFocusRequest: AudioFocusRequest? = null

    // ===== STATE =====
    private var pausedByVoice = false
    private var musicEverPlayed = false
    private var lastMusicActiveTime = 0L

    // ===== THREADING =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    // ===== PROFILE RECEIVER =====
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ProfileIntent.ACTION_PROFILE_CHANGED) return

            val name = intent.getStringExtra(ProfileIntent.EXTRA_PROFILE_NAME)
            val newProfile = runCatching {
                AudioProfile.valueOf(name ?: "")
            }.getOrNull()

            if (newProfile == null || newProfile == currentProfile) return

            Log.i(TAG, "Profile switched → ${newProfile.displayName}")
            currentProfile = newProfile

            if (vadProcessor.isRunning()) {
                vadProcessor.stop()
                initializeVad()
                applyUserResumeDelay()
                vadProcessor.start()
            }
        }
    }

    // ======================
    // SERVICE LIFECYCLE
    // ======================

    override fun onCreate() {
        super.onCreate()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vadProcessor = VadProcessor()

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)

        val filter = IntentFilter(ProfileIntent.ACTION_PROFILE_CHANGED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(profileReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopServiceCompletely("Manual stop")
            return START_NOT_STICKY
        }

        Settings.setServiceRunning(applicationContext, true)
        sendBroadcast(Intent(ACTION_SERVICE_STARTED))

        startForeground(
            NOTIFICATION_ID,
            createNotification("Listening (${currentProfile.displayName})")
        )

        lastMusicActiveTime = System.currentTimeMillis()

        initializeVad()
        applyUserResumeDelay() // 🔑 RESTORES SLIDER BEHAVIOR
        handler.post(musicMonitorLoop)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        vadProcessor.release()
        abandonAudioFocus()
        workerThread.quitSafely()

        unregisterReceiver(profileReceiver)

        Settings.setServiceRunning(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ======================
    // VAD INIT
    // ======================

    private fun initializeVad() {
        Log.i(TAG, "Initializing VAD → ${currentProfile.displayName}")

        if (!vadProcessor.initialize(this, currentProfile)) {
            stopServiceCompletely("VAD init failed")
        }
    }

    // ======================
    // USER RESUME DELAY (CRITICAL)
    // ======================

    private fun applyUserResumeDelay() {
        val userDelayMs =
            Settings.getSilenceDurationSeconds(applicationContext) * 1000L

        vadProcessor.applyUserResumeDelay(userDelayMs)
    }

    // ======================
    // MUSIC MONITOR LOOP
    // ======================

    private val musicMonitorLoop = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                val musicPlaying = audioManager.isMusicActive

                if (musicPlaying || pausedByVoice) {
                    musicEverPlayed = true
                    lastMusicActiveTime = now

                    if (!vadProcessor.isRunning()) {
                        vadProcessor.start()
                    }

                    // 🔁 LIVE SLIDER UPDATE
                    applyUserResumeDelay()

                } else {
                    if (vadProcessor.isRunning()) {
                        vadProcessor.stop()
                    }

                    if (musicEverPlayed &&
                        now - lastMusicActiveTime >= AUTO_STOP_TIMEOUT_MS
                    ) {
                        stopServiceCompletely("Auto-stop")
                        return
                    }
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

        val request = AudioFocusRequest.Builder(
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )
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
        }
    }

    private fun resumeMusic() {
        if (!pausedByVoice) return

        abandonAudioFocus()
        pausedByVoice = false
        updateNotification("Listening (${currentProfile.displayName})")
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

    override fun onSpeechDetected() = pauseMusic()

    override fun onSpeechEnded() = resumeMusic()

    override fun onVadError(error: String) {
        Log.e(TAG, "VAD ERROR: $error")
        stopServiceCompletely("VAD error")
    }

    // ======================
    // SERVICE STOP
    // ======================

    private fun stopServiceCompletely(reason: String) {
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
            .setContentIntent(stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, createNotification(text))
    }
}
