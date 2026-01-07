package com.rohit.voicepause

import android.content.Context

object Settings {

    private const val PREFS = "voicepause_settings"
    private const val KEY_VOICE_SENSITIVITY = "voice_sensitivity" // 1–100
    private const val KEY_SILENCE_SECONDS = "silence_seconds"     // 1–20

    // ---------- VOICE SENSITIVITY ----------

    fun getVoiceSensitivity(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VOICE_SENSITIVITY, 50) // default middle
    }

    fun setVoiceSensitivity(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_VOICE_SENSITIVITY, value.coerceIn(1, 100))
            .apply()
    }

    // Maps 1–100 → RMS 300–3000
    // Maps 1–100 → RMS 50–3000
    fun getVoiceThreshold(context: Context): Int {
        val sensitivity = getVoiceSensitivity(context) // 1..100
        return (50 + (sensitivity / 100f) * 2950).toInt()
    }


    // ---------- SILENCE DURATION ----------

    fun getSilenceSeconds(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_SILENCE_SECONDS, 10)
    }

    fun setSilenceSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SILENCE_SECONDS, seconds.coerceIn(1, 20))
            .apply()
    }

    fun getSilenceDuration(context: Context): Long {
        return getSilenceSeconds(context) * 1000L
    }
}
