package com.rohit.voicepause

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rohit.voicepause.audio.VadProcessor

/**
 * Foreground service for voice-activated music pause/resume
 *
 * Uses heuristic VAD with temporal validation to detect speech and
 * pause/resume music during conversations.
 */
class VoiceMonitorService : Service(), VadProcessor.VadProcessorListener {

    private lateinit var audioManager: AudioManager
    private lateinit var vadProcessor: VadProcessor

    // ===== MUSIC CONTROL STATE =====
    private var hasAudioFocus = false
    private var pausedByVoice = false
    private var speechEventInProgress = false

    // ===== MUSIC IDLE DETECTION =====
    private var lastMusicActiveTime = 0L
    private var musicEverPlayed = false
    private val AUTO_STOP_TIMEOUT_MS = 60_000L

    // ===== BACKGROUND PROCESSING =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    companion object {
        private const val TAG = "VoiceMonitorService"

        const val ACTION_SERVICE_STOPPED = "com.rohit.voicepause.ACTION_SERVICE_STOPPED"
        private const val NOTIFICATION_ID = 100
        private const val ACTION_STOP_SERVICE = "com.rohit.voicepause.ACTION_STOP_SERVICE"
    }

    // ======================
    // SERVICE LIFECYCLE
    // ======================

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        vadProcessor = VadProcessor()

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        Settings.setServiceRunning(applicationContext, true)
        startForeground(NOTIFICATION_ID, createNotification())

        lastMusicActiveTime = System.currentTimeMillis()

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopServiceCompletely("Manual stop")
            return START_NOT_STICKY
        }

        initializeVadProcessor()

        handler.removeCallbacks(musicMonitoringLoop)
        handler.post(musicMonitoringLoop)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")

        vadProcessor.release()

        handler.removeCallbacksAndMessages(null)
        workerThread.quitSafely()

        releaseAudioFocusIfHeld()
        Settings.setServiceRunning(applicationContext, false)
    }

    // ======================
    // VAD PROCESSOR SETUP
    // ======================

    private fun initializeVadProcessor() {
        val silenceDelayMs = Settings.getSilenceDuration(applicationContext)

        val success = vadProcessor.initialize(
            listener = this,
            silenceDelayMs = silenceDelayMs
        )

        if (!success) {
            Log.e(TAG, "Failed to initialize VAD processor")
            stopServiceCompletely("VAD initialization failed")
        } else {
            Log.i(TAG, "VAD processor initialized")
        }
    }

    // ======================
    // MUSIC MONITORING LOOP
    // ======================

    private val musicMonitoringLoop = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                val musicPlaying = audioManager.isMusicActive

                if (musicPlaying) {
                    musicEverPlayed = true
                    lastMusicActiveTime = now
                    startVadIfNeeded()
                } else {
                    stopVadIfNeeded()

                    if (musicEverPlayed && !pausedByVoice) {
                        val idleDuration = now - lastMusicActiveTime
                        if (idleDuration >= AUTO_STOP_TIMEOUT_MS) {
                            stopServiceCompletely("Auto-stop: music idle")
                            return
                        }
                    }
                }

                updateVadSettings()
                handler.postDelayed(this, 1000)

            } catch (e: Exception) {
                Log.e(TAG, "Music monitoring error", e)
                stopServiceCompletely("Monitoring error: ${e.message}")
            }
        }
    }

    private fun startVadIfNeeded() {
        if (!vadProcessor.isRunning()) {
            if (!vadProcessor.start()) {
                stopServiceCompletely("Failed to start VAD")
            }
        }
    }

    private fun stopVadIfNeeded() {
        if (vadProcessor.isRunning()) {
            vadProcessor.stop()
        }
    }

    private fun updateVadSettings() {
        val currentSilenceDelay = Settings.getSilenceDuration(applicationContext)
        if (currentSilenceDelay != vadProcessor.getSilenceDelay()) {
            vadProcessor.setSilenceDelay(currentSilenceDelay)
        }
    }

    // ======================
    // MUSIC CONTROL
    // ======================

    private fun pauseMusic() {
        if (pausedByVoice) return

        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasAudioFocus = true
            pausedByVoice = true
            updateNotification("Voice detected – Music paused")
        }
    }

    private fun resumeMusic() {
        if (!pausedByVoice) return

        releaseAudioFocusIfHeld()
        pausedByVoice = false
        speechEventInProgress = false
        updateNotification("Listening for voice")
    }

    private fun releaseAudioFocusIfHeld() {
        if (hasAudioFocus) {
            audioManager.abandonAudioFocus(null)
            hasAudioFocus = false
        }
    }

    // ======================
    // VAD CALLBACKS
    // ======================

    override fun onSpeechDetected() {
        if (!speechEventInProgress) {
            speechEventInProgress = true
            pauseMusic()
        }
    }

    override fun onSpeechEnded() {
        resumeMusic()
    }

    override fun onVadError(error: String) {
        Log.e(TAG, "VAD error: $error")
        stopServiceCompletely("VAD error: $error")
    }

    // ======================
    // SERVICE CONTROL
    // ======================

    private fun stopServiceCompletely(reason: String) {
        Log.i(TAG, "Stopping service: $reason")

        Settings.setServiceRunning(applicationContext, false)
        handler.removeCallbacksAndMessages(null)
        vadProcessor.stop()
        releaseAudioFocusIfHeld()

        stopForeground(true)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        stopSelf()
    }

    // ======================
    // NOTIFICATION
    // ======================

    private fun createNotification(): Notification =
        createNotificationWithText("Listening for voice")

    private fun updateNotification(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotificationWithText(text))
    }

    private fun createNotificationWithText(text: String): Notification {
        val channelId = "voice_pause_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VoicePause Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Voice-activated music pause service"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, VoiceMonitorService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoicePause Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
            .build()
    }
}