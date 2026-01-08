package com.rohit.voicepause

import android.content.Context

object Settings {

    private const val PREFS = "voicepause_prefs"

    private const val KEY_SILENCE_DURATION = "silence_duration"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_VAD_AGGRESSIVENESS = "vad_aggressiveness"

    // ======================
    // Silence duration
    // ======================

    fun setSilenceDuration(context: Context, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SILENCE_DURATION, value)
            .apply()
    }

    fun getSilenceDuration(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SILENCE_DURATION, 800L) // default 800ms (was 10s)
    }

    // ======================
    // VAD Aggressiveness
    // ======================

    fun setVadAggressiveness(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VAD_AGGRESSIVENESS, value)
            .apply()
    }

    fun getVadAggressiveness(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VAD_AGGRESSIVENESS, 1) // default: low bitrate optimized
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
    // Migration from old settings
    // ======================

    /**
     * Migrate old voice threshold setting to new VAD aggressiveness
     * This helps users upgrading from the RMS-based version
     */
    fun migrateOldSettings(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        
        // Check if we have old voice threshold setting
        if (prefs.contains("voice_threshold") && !prefs.contains(KEY_VAD_AGGRESSIVENESS)) {
            val oldThreshold = prefs.getInt("voice_threshold", 200)
            
            // Convert old threshold to VAD aggressiveness
            val vadAggressiveness = when {
                oldThreshold < 100 -> 0   // Very sensitive -> Quality mode
                oldThreshold < 300 -> 1   // Normal -> Low bitrate
                oldThreshold < 1000 -> 2  // Less sensitive -> Aggressive
                else -> 3                 // Very insensitive -> Very aggressive
            }
            
            // Save new setting and remove old one
            prefs.edit()
                .putInt(KEY_VAD_AGGRESSIVENESS, vadAggressiveness)
                .remove("voice_threshold")
                .apply()
        }
        
        // Update default silence duration from 10s to 800ms for better UX
        if (!prefs.contains(KEY_SILENCE_DURATION)) {
            setSilenceDuration(context, 800L)
        }
    }
}
