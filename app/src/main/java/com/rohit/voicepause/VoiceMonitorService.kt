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
        private const val MONITOR_INTERVAL_MS = 1_000L
    }

    // ===== SYSTEM =====
    private lateinit var audioManager: AudioManager
    private lateinit var vadProcessor: VadProcessor
    private lateinit var currentProfile: AudioProfile

    // ===== AUDIO FOCUS =====
    private var audioFocusRequest: AudioFocusRequest? = null

    // ===== STATE =====
    private var pausedByVoice = false
    private var musicEverPlayed = false
    private var lastMusicActiveTime = 0L

    // ===== THREADING =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler
    private var resumeRunnable: Runnable? = null

    // ===== PROFILE PARAM =====
    private var pauseHoldMs: Long = 5_000L   // safety fallback

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

        currentProfile = Settings.getSelectedProfile(applicationContext)
        applyProfileParams()

        Log.i(
            TAG,
            "[SERVICE CREATED] profile=${currentProfile.displayName}, pauseHoldMs=$pauseHoldMs"
        )
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
        handler.post(musicMonitorLoop)

        Log.i(TAG, "[SERVICE STARTED]")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "[SERVICE DESTROYED]")

        handler.removeCallbacksAndMessages(null)
        vadProcessor.release()
        abandonAudioFocus()
        workerThread.quitSafely()

        Settings.setServiceRunning(applicationContext, false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ======================
    // PROFILE HANDLING
    // ======================

    private fun applyProfileParams() {
        pauseHoldMs =
            if (currentProfile.isCustom) {
                Settings.getCustomPauseDurationMs(applicationContext)
            } else {
                currentProfile.pauseHoldMs
            }

        Log.i(
            TAG,
            "[PROFILE APPLIED] ${currentProfile.displayName} → pauseHoldMs=$pauseHoldMs"
        )
    }

    private fun reloadProfileLive(newProfile: AudioProfile) {
        Log.i(
            TAG,
            "[PROFILE HOT-RELOAD] ${currentProfile.displayName} → ${newProfile.displayName}"
        )

        currentProfile = newProfile
        applyProfileParams()

        resumeRunnable?.let { handler.removeCallbacks(it) }
        resumeRunnable = null

        if (vadProcessor.isRunning()) {
            vadProcessor.stop()
        }
        vadProcessor.release()

        vadProcessor = VadProcessor()
        initializeVad()

        if (audioManager.isMusicActive || pausedByVoice) {
            vadProcessor.start()
            applyCustomControlsIfNeeded()
        }

        Log.i(TAG, "[PROFILE HOT-RELOAD COMPLETE]")
    }

    // ======================
    // VAD SETUP
    // ======================

    private fun initializeVad() {
        if (!vadProcessor.initialize(this, currentProfile)) {
            stopServiceCompletely("VAD init failed")
        }
    }

    private fun applyCustomControlsIfNeeded() {
        if (!currentProfile.isCustom) return

        val sensitivity =
            Settings.getCustomVoiceSensitivity(applicationContext)

        vadProcessor.applyUserSensitivity(sensitivity)

        Log.i(TAG, "[CUSTOM SETTINGS] sensitivity=$sensitivity")
    }

    // ======================
    // MUSIC MONITOR LOOP
    // ======================

    private val musicMonitorLoop = object : Runnable {
        override fun run() {

            // 🔥 AUTHORITATIVE PROFILE CHECK
            val selectedProfile =
                Settings.getSelectedProfile(applicationContext)

            if (selectedProfile != currentProfile) {
                reloadProfileLive(selectedProfile)
            }

            val now = System.currentTimeMillis()
            val musicPlaying = audioManager.isMusicActive

            if (musicPlaying || pausedByVoice) {
                musicEverPlayed = true
                lastMusicActiveTime = now

                if (!vadProcessor.isRunning()) {
                    vadProcessor.start()
                    Log.d(TAG, "[MONITOR] VAD started")
                }
            } else {
                if (vadProcessor.isRunning()) {
                    vadProcessor.stop()
                    Log.d(TAG, "[MONITOR] VAD stopped (music inactive)")
                }

                if (musicEverPlayed &&
                    now - lastMusicActiveTime >= AUTO_STOP_TIMEOUT_MS
                ) {
                    stopServiceCompletely("Auto-stop")
                    return
                }
            }

            handler.postDelayed(this, MONITOR_INTERVAL_MS)
        }
    }

    // ======================
    // AUDIO CONTROL
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

            Log.i(TAG, "[AUDIO] Music paused (reason=VOICE)")
            updateNotification("Voice detected – Music paused")
        }
    }

    private fun scheduleResume() {
        resumeRunnable?.let { handler.removeCallbacks(it) }

        resumeRunnable = Runnable {
            resumeMusic()
        }

        handler.postDelayed(resumeRunnable!!, pauseHoldMs)

        Log.i(TAG, "[AUDIO] Resume scheduled in ${pauseHoldMs}ms")
    }

    private fun resumeMusic() {
        resumeRunnable?.let { handler.removeCallbacks(it) }
        resumeRunnable = null

        if (!pausedByVoice) return

        abandonAudioFocus()
        pausedByVoice = false

        Log.i(TAG, "[AUDIO] Music resumed (reason=SILENCE_TIMEOUT)")
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

    override fun onSpeechDetected() {
        Log.i(TAG, "[VAD] Voice detected")
        pauseMusic()
        scheduleResume()
    }

    override fun onVadError(error: String) {
        Log.e(TAG, "[VAD ERROR] $error")
        stopServiceCompletely("VAD error")
    }

    // ======================
    // SERVICE STOP
    // ======================

    private fun stopServiceCompletely(reason: String) {
        Log.i(TAG, "[SERVICE STOP] reason=$reason")

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
