package com.rohit.voicepause

import android.content.Context

object Settings {

    private const val PREFS = "voicepause_settings"

    private const val KEY_VOICE_THRESHOLD = "voice_threshold"
    private const val KEY_SILENCE_DURATION = "silence_duration"

    // Defaults (safe + tested)
    private const val DEFAULT_VOICE_THRESHOLD = 2000
    private const val DEFAULT_SILENCE_DURATION = 2000L

    fun getVoiceThreshold(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VOICE_THRESHOLD, DEFAULT_VOICE_THRESHOLD)

    fun setVoiceThreshold(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VOICE_THRESHOLD, value)
            .apply()
    }

    fun getSilenceDuration(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SILENCE_DURATION, DEFAULT_SILENCE_DURATION)

    fun setSilenceDuration(context: Context, value: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_SILENCE_DURATION, value)
            .apply()
    }
}
