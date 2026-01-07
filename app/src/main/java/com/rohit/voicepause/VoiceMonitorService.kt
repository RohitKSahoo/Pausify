package com.rohit.voicepause

import android.app.*
import android.content.Intent
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class VoiceMonitorService : Service() {

    private lateinit var audioManager: AudioManager

    // ===== MIC THREAD =====
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var micThread: Thread? = null
    @Volatile private var micRunning = false

    // ===== VOICE LOGIC =====
    @Volatile private var voiceThreshold = 200        // RMS threshold
    @Volatile private var silenceTimeoutMs = 10_000L  // ms

    private var lastVoiceTime = 0L
    private var silenceTimerStarted = false

    private var hasAudioFocus = false
    private var pausedByVoice = false

    // ===== MUSIC IDLE =====
    private var lastMusicActiveTime = 0L
    private var musicEverPlayed = false
    private val AUTO_STOP_TIMEOUT_MS = 60_000L

    // ===== POLLING =====
    private lateinit var workerThread: HandlerThread
    private lateinit var handler: Handler

    companion object {
        const val ACTION_SERVICE_STOPPED =
            "com.rohit.voicepause.ACTION_SERVICE_STOPPED"

        private const val NOTIFICATION_ID = 100
        private const val ACTION_STOP_SERVICE =
            "com.rohit.voicepause.ACTION_STOP_SERVICE"
    }

    // ======================
    // LIFECYCLE
    // ======================

    override fun onCreate() {
        super.onCreate()
        Log.d("VoicePause", "Service created")

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        workerThread = HandlerThread("VoicePauseWorker")
        workerThread.start()
        handler = Handler(workerThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d("VoicePause", "Service started")

        startForeground(NOTIFICATION_ID, createNotification())

        lastMusicActiveTime = System.currentTimeMillis()

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopServiceCompletely("Manual stop")
            return START_NOT_STICKY
        }

        handler.removeCallbacks(musicChecker)
        handler.post(musicChecker)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ======================
    // MUSIC CHECK LOOP
    // ======================

    private val musicChecker = object : Runnable {
        override fun run() {

            voiceThreshold = Settings.getVoiceThreshold(applicationContext)
            silenceTimeoutMs = Settings.getSilenceDuration(applicationContext)

            val now = System.currentTimeMillis()
            val musicPlaying = audioManager.isMusicActive

            if (musicPlaying) {
                musicEverPlayed = true
                lastMusicActiveTime = now
                startMicIfNeeded()
            } else {
                stopMicIfNeeded()

                // 🚫 Do NOT auto-stop while paused by voice
                if (musicEverPlayed && !pausedByVoice) {
                    val idle = now - lastMusicActiveTime
                    if (idle >= AUTO_STOP_TIMEOUT_MS) {
                        stopServiceCompletely("Auto-stop: music idle")
                        return
                    }
                }
            }

            handler.postDelayed(this, 1000)
        }
    }

    // ======================
    // MIC HANDLING
    // ======================

    private fun startMicIfNeeded() {
        if (micRunning) return

        micRunning = true
        pausedByVoice = false
        silenceTimerStarted = false

        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        micThread = Thread {
            val buffer = ShortArray(bufferSize)

            while (micRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read > 0) {
                    val rms = calculateRms(buffer, read)

                    if (rms > voiceThreshold) {
                        onVoiceDetected()
                    } else {
                        checkForSilence()
                    }
                }
            }

            releaseMic()
        }

        micThread?.start()
        Log.d("VoicePause", "Mic started")
    }

    private fun stopMicIfNeeded() {
        if (!micRunning) return

        micRunning = false
        try {
            micThread?.join(300)
        } catch (_: Exception) {}

        micThread = null
    }

    private fun releaseMic() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        if (hasAudioFocus) {
            audioManager.abandonAudioFocus(null)
            hasAudioFocus = false
        }

        Log.d("VoicePause", "Mic released")
    }

    // ======================
    // PAUSE / RESUME LOGIC
    // ======================

    private fun onVoiceDetected() {
        lastVoiceTime = System.currentTimeMillis()
        silenceTimerStarted = false

        if (pausedByVoice) return

        val result = audioManager.requestAudioFocus(
            null,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            pausedByVoice = true
            hasAudioFocus = true
            Log.d("VoicePause", "Music paused by voice")
        }
    }

    private fun checkForSilence() {
        if (!pausedByVoice) return

        val now = System.currentTimeMillis()

        if (!silenceTimerStarted) {
            silenceTimerStarted = true
            lastVoiceTime = now
            return
        }

        val silence = now - lastVoiceTime

        if (silence >= silenceTimeoutMs) {
            audioManager.abandonAudioFocus(null)
            hasAudioFocus = false
            pausedByVoice = false
            silenceTimerStarted = false

            Log.d("VoicePause", "Music resumed after silence")
        }
    }

    // ======================
    // STOP EVERYTHING
    // ======================

    private fun stopServiceCompletely(reason: String) {
        Log.d("VoicePause", "Stopping service → $reason")

        micRunning = false
        handler.removeCallbacksAndMessages(null)

        stopForeground(true)
        sendBroadcast(Intent(ACTION_SERVICE_STOPPED))
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        micRunning = false
        workerThread.quitSafely()
        Log.d("VoicePause", "Service destroyed")
    }

    // ======================
    // UTIL
    // ======================

    private fun calculateRms(buffer: ShortArray, read: Int): Double {
        var sum = 0.0
        for (i in 0 until read) sum += buffer[i] * buffer[i]
        return sqrt(sum / read)
    }

    // ======================
    // NOTIFICATION
    // ======================

    private fun createNotification(): Notification {
        val channelId = "voice_pause_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "VoicePause",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
        }

        val stopIntent = Intent(this, VoiceMonitorService::class.java)
            .apply { action = ACTION_STOP_SERVICE }

        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VoicePause running")
            .setContentText("Listening for voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent)
            .build()
    }
}
