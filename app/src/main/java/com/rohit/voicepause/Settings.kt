package com.rohit.voicepause

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.rohit.voicepause.audio.AudioProfile

object Settings {

    private const val TAG = "VoicePause/Settings"
    private const val PREFS_NAME = "voice_pause_prefs"

    // ===== KEYS =====
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_SELECTED_PROFILE = "selected_audio_profile"

    // ===== CUSTOM PROFILE KEYS =====
    private const val KEY_CUSTOM_SILENCE_DURATION = "custom_silence_duration_sec"
    private const val KEY_CUSTOM_VOICE_SENSITIVITY = "custom_voice_sensitivity"

    // ===== DEFAULTS =====
    private const val DEFAULT_CUSTOM_SILENCE_DURATION = 2      // seconds
    private const val DEFAULT_CUSTOM_VOICE_SENSITIVITY = 1.0f // multiplier

    // ===== PREF ACCESS =====
    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ======================
    // PROFILE SELECTION
    // ======================

    fun getSelectedProfile(context: Context): AudioProfile {
        val name = prefs(context).getString(
            KEY_SELECTED_PROFILE,
            AudioProfile.BUSY.name
        )

        val profile = runCatching {
            AudioProfile.valueOf(name!!)
        }.getOrDefault(AudioProfile.BUSY)

        Log.i(TAG, "[GET] Selected profile → ${profile.displayName}")
        return profile
    }

    fun setSelectedProfile(context: Context, profile: AudioProfile) {
        prefs(context).edit()
            .putString(KEY_SELECTED_PROFILE, profile.name)
            .apply()

        Log.i(TAG, "[SET] Selected profile → ${profile.displayName}")
    }

    // ======================
    // SERVICE STATE
    // ======================

    fun isServiceRunning(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SERVICE_RUNNING, false)
    }

    fun setServiceRunning(context: Context, running: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()

        Log.i(TAG, "[SERVICE] running=$running")
    }

    // ======================
    // CUSTOM PROFILE — PAUSE DURATION
    // ======================

    fun getCustomPauseDurationMs(context: Context): Long {
        val seconds = prefs(context).getInt(
            KEY_CUSTOM_SILENCE_DURATION,
            DEFAULT_CUSTOM_SILENCE_DURATION
        )

        val ms = seconds * 1000L

        Log.i(
            TAG,
            "[CUSTOM] Pause duration → ${ms}ms ($seconds sec)"
        )

        return ms
    }

    fun setCustomSilenceDurationSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(KEY_CUSTOM_SILENCE_DURATION, seconds)
            .apply()

        Log.i(
            TAG,
            "[CUSTOM] Pause duration set → ${seconds}s"
        )
    }

    // ======================
    // CUSTOM PROFILE — VOICE SENSITIVITY
    // ======================

    fun getCustomVoiceSensitivity(context: Context): Float {
        val value = prefs(context).getFloat(
            KEY_CUSTOM_VOICE_SENSITIVITY,
            DEFAULT_CUSTOM_VOICE_SENSITIVITY
        )

        Log.i(
            TAG,
            "[CUSTOM] Voice sensitivity → $value"
        )

        return value
    }

    fun setCustomVoiceSensitivity(context: Context, value: Float) {
        prefs(context).edit()
            .putFloat(KEY_CUSTOM_VOICE_SENSITIVITY, value)
            .apply()

        Log.i(
            TAG,
            "[CUSTOM] Voice sensitivity set → $value"
        )
    }

    // ======================
    // MIGRATION (FUTURE)
    // ======================

    fun migrate(context: Context) {
        // No-op for now
        // Reserved for future schema changes
        Log.i(TAG, "[MIGRATION] No action needed")
    }
}
