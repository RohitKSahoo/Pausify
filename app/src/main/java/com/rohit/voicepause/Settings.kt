package com.rohit.voicepause

import android.content.Context
import android.util.Log

object Settings {

    private const val TAG = "VoicePause/Settings"
    private const val PREFS = "voicepause_prefs"

    private const val KEY_SILENCE_DURATION_MS = "silence_duration_ms"
    private const val KEY_SERVICE_RUNNING = "service_running"

    // Silence delay bounds
    private const val MIN_SILENCE_SEC = 1
    private const val MAX_SILENCE_SEC = 10
    private const val DEFAULT_SILENCE_SEC = 2

    // ======================
    // Silence duration
    // ======================

    /**
     * Set silence duration using SECONDS (UI-friendly).
     * Range enforced: 1s – 10s
     */
    fun setSilenceDurationSeconds(context: Context, seconds: Int) {
        val clampedSec = seconds.coerceIn(MIN_SILENCE_SEC, MAX_SILENCE_SEC)
        val millis = clampedSec * 1000L

        Log.i(TAG, "Saving silence delay: ${clampedSec}s (${millis}ms)")

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SILENCE_DURATION_MS, millis)
            .apply()
    }

    /**
     * Get silence duration in MILLISECONDS (service-friendly)
     */
    fun getSilenceDuration(context: Context): Long {
        val millis = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SILENCE_DURATION_MS, DEFAULT_SILENCE_SEC * 1000L)

        Log.v(TAG, "Loaded silence delay: ${millis}ms")
        return millis
    }

    /**
     * Get silence duration in SECONDS (UI-friendly)
     */
    fun getSilenceDurationSeconds(context: Context): Int {
        val millis = getSilenceDuration(context)
        return (millis / 1000L).toInt()
    }

    // ======================
    // Service state (UI sync)
    // ======================

    fun setServiceRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()
    }

    fun isServiceRunning(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_RUNNING, false)
    }

    // ======================
    // Migration
    // ======================

    fun migrate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // If old value exists, normalize it
        if (prefs.contains("silence_duration")) {
            val old = prefs.getLong("silence_duration", 2000L)
            val seconds = (old / 1000L).toInt().coerceIn(MIN_SILENCE_SEC, MAX_SILENCE_SEC)

            Log.w(TAG, "Migrating old silence duration → ${seconds}s")

            prefs.edit()
                .remove("silence_duration")
                .putLong(KEY_SILENCE_DURATION_MS, seconds * 1000L)
                .apply()
        }

        if (!prefs.contains(KEY_SILENCE_DURATION_MS)) {
            setSilenceDurationSeconds(context, DEFAULT_SILENCE_SEC)
        }
    }
}
