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
    private const val KEY_DUCK_VOLUME = "duck_volume_enabled"
    private const val KEY_AUTO_RESUME = "auto_resume_enabled"
    private const val KEY_ENGINE_MODE = "engine_mode"
    
    // ML Specific
    private const val KEY_ML_VALIDATION_ENABLED = "ml_validation_enabled"
    private const val KEY_ML_CONFIDENCE_THRESHOLD = "ml_confidence_threshold"

    // Speaker Verification Specific
    private const val KEY_SPEAKER_VERIFICATION_ENABLED = "speaker_verification_enabled"
    private const val KEY_USER_EMBEDDING = "user_embedding"
    private const val KEY_SPEAKER_THRESHOLD = "speaker_similarity_threshold"

    // ===== CUSTOM PROFILE KEYS =====
    private const val KEY_CUSTOM_PAUSE_SEC = "custom_pause_sec"
    private const val KEY_CUSTOM_SENSITIVITY = "custom_voice_sensitivity"
    private const val KEY_CUSTOM_MIN_SPEECH = "custom_min_speech_ms"
    private const val KEY_CUSTOM_MIN_ENERGY = "custom_min_energy"
    private const val KEY_CUSTOM_VAD_MODE = "custom_vad_mode"

    // ===== DEFAULTS (Safe) =====
    private const val DEFAULT_CUSTOM_PAUSE_SEC = 3
    private const val DEFAULT_CUSTOM_SENSITIVITY = 1.0f
    private const val DEFAULT_CUSTOM_MIN_SPEECH = 250L
    private const val DEFAULT_CUSTOM_MIN_ENERGY = 400
    private const val DEFAULT_CUSTOM_VAD_MODE = 2
    private const val DEFAULT_DUCK_VOLUME = true
    private const val DEFAULT_AUTO_RESUME = false
    private const val DEFAULT_ENGINE_MODE = 1
    
    private const val DEFAULT_ML_VALIDATION_ENABLED = true
    private const val DEFAULT_ML_CONFIDENCE_THRESHOLD = 0.35f

    private const val DEFAULT_SPEAKER_VERIFICATION_ENABLED = false
    private const val DEFAULT_SPEAKER_THRESHOLD = 0.75f

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

    fun isServiceRunning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_RUNNING, false)

    fun setServiceRunning(context: Context, running: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()

        Log.i(TAG, "[SERVICE] running=$running")
    }

    // ======================
    // ENGINE SETTINGS (DUCK, RESUME, MODE)
    // ======================

    fun isDuckVolumeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DUCK_VOLUME, DEFAULT_DUCK_VOLUME)

    fun setDuckVolumeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DUCK_VOLUME, enabled).apply()
    }

    fun isAutoResumeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RESUME, DEFAULT_AUTO_RESUME)

    fun setAutoResumeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    fun getEngineMode(context: Context): Int =
        prefs(context).getInt(KEY_ENGINE_MODE, DEFAULT_ENGINE_MODE)

    fun setEngineMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_ENGINE_MODE, mode).apply()
    }

    // ======================
    // ML SETTINGS
    // ======================

    fun isMlValidationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ML_VALIDATION_ENABLED, DEFAULT_ML_VALIDATION_ENABLED)

    fun setMlValidationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ML_VALIDATION_ENABLED, enabled).apply()
    }

    fun getMlConfidenceThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_ML_CONFIDENCE_THRESHOLD, DEFAULT_ML_CONFIDENCE_THRESHOLD)

    fun setMlConfidenceThreshold(context: Context, threshold: Float) {
        prefs(context).edit().putFloat(KEY_ML_CONFIDENCE_THRESHOLD, threshold).apply()
    }

    // ======================
    // SPEAKER VERIFICATION SETTINGS
    // ======================

    fun isSpeakerVerificationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEAKER_VERIFICATION_ENABLED, DEFAULT_SPEAKER_VERIFICATION_ENABLED)

    fun setSpeakerVerificationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEAKER_VERIFICATION_ENABLED, enabled).apply()
    }

    fun getSpeakerThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_SPEAKER_THRESHOLD, DEFAULT_SPEAKER_THRESHOLD)

    fun setSpeakerThreshold(context: Context, threshold: Float) {
        prefs(context).edit().putFloat(KEY_SPEAKER_THRESHOLD, threshold).apply()
    }

    fun getUserEmbedding(context: Context): FloatArray? {
        val encoded = prefs(context).getString(KEY_USER_EMBEDDING, null) ?: return null
        return try {
            encoded.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    fun setUserEmbedding(context: Context, embedding: FloatArray) {
        val encoded = embedding.joinToString(",")
        prefs(context).edit().putString(KEY_USER_EMBEDDING, encoded).apply()
    }

    fun clearUserEmbedding(context: Context) {
        prefs(context).edit().remove(KEY_USER_EMBEDDING).apply()
    }

    // ======================
    // CUSTOM: PAUSE
    // ======================

    fun getCustomPauseDurationMs(context: Context): Long {
        val sec = prefs(context).getInt(
            KEY_CUSTOM_PAUSE_SEC,
            DEFAULT_CUSTOM_PAUSE_SEC
        )

        val ms = sec * 1000L

        Log.i(TAG, "[CUSTOM] pause=$ms ms")
        return ms
    }

    fun setCustomPauseSeconds(context: Context, sec: Int) {
        prefs(context).edit()
            .putInt(KEY_CUSTOM_PAUSE_SEC, sec)
            .apply()

        Log.i(TAG, "[CUSTOM] pause set=$sec s")
    }

    // ======================
    // CUSTOM: SENSITIVITY
    // ======================

    fun getCustomVoiceSensitivity(context: Context): Float =
        prefs(context).getFloat(
            KEY_CUSTOM_SENSITIVITY,
            DEFAULT_CUSTOM_SENSITIVITY
        )

    fun setCustomVoiceSensitivity(context: Context, value: Float) {
        prefs(context).edit()
            .putFloat(KEY_CUSTOM_SENSITIVITY, value)
            .apply()

        Log.i(TAG, "[CUSTOM] sensitivity=$value")
    }

    // ======================
    // CUSTOM: MIN SPEECH
    // ======================

    fun getCustomMinSpeechMs(context: Context): Long =
        prefs(context).getLong(
            KEY_CUSTOM_MIN_SPEECH,
            DEFAULT_CUSTOM_MIN_SPEECH
        )

    fun setCustomMinSpeechMs(context: Context, ms: Long) {
        prefs(context).edit()
            .putLong(KEY_CUSTOM_MIN_SPEECH, ms)
            .apply()

        Log.i(TAG, "[CUSTOM] minSpeech=$ms")
    }

    // ======================
    // CUSTOM: ENERGY
    // ======================

    fun getCustomMinEnergy(context: Context): Int =
        prefs(context).getInt(
            KEY_CUSTOM_MIN_ENERGY,
            DEFAULT_CUSTOM_MIN_ENERGY
        )

    fun setCustomMinEnergy(context: Context, energy: Int) {
        prefs(context).edit()
            .putInt(KEY_CUSTOM_MIN_ENERGY, energy)
            .apply()

        Log.i(TAG, "[CUSTOM] minEnergy=$energy")
    }

    // ======================
    // CUSTOM: VAD MODE
    // ======================

    fun getCustomVadMode(context: Context): Int =
        prefs(context).getInt(
            KEY_CUSTOM_VAD_MODE,
            DEFAULT_CUSTOM_VAD_MODE
        )

    fun setCustomVadMode(context: Context, mode: Int) {
        prefs(context).edit()
            .putInt(KEY_CUSTOM_VAD_MODE, mode)
            .apply()

        Log.i(TAG, "[CUSTOM] vadMode=$mode")
    }

    // ======================
    // MIGRATION
    // ======================

    fun migrate(context: Context) {
        Log.i(TAG, "[MIGRATION] No-op")
    }
}
