package com.rohit.voicepause.audio

enum class AudioProfile(
    val displayName: String,

    // How aggressive the WebRTC VAD is (0 = least, 3 = most)
    val vadAggressiveness: Int,

    // How long speech must be present before it counts
    val minSpeechMs: Long,

    // Minimum loudness (RMS) required to accept speech
    val minVoiceEnergy: Int,

    // How long music stays paused after last detected voice
    val pauseHoldMs: Long,

    val isCustom: Boolean = false
) {

    QUIET(
        displayName = "Quiet Room",
        vadAggressiveness = 3,
        minSpeechMs = 100L,
        minVoiceEnergy = 300,
        pauseHoldMs = 2_000L
    ),

    BUSY(
        displayName = "Busy Room",
        vadAggressiveness = 2,
        minSpeechMs = 250L,
        minVoiceEnergy = 500,
        pauseHoldMs = 5_000L
    ),

    TRAFFIC(
        displayName = "Traffic / Outdoors",
        vadAggressiveness = 1,
        minSpeechMs = 700L,
        minVoiceEnergy = 1_200,
        pauseHoldMs = 7_000L
    ),

    /**
     * User-tuned profile.
     * All values are loaded dynamically from Settings.
     * These are SENTINELS and MUST NOT be used directly.
     */
    CUSTOM(
        displayName = "Custom",
        vadAggressiveness = -1,
        minSpeechMs = -1L,
        minVoiceEnergy = -1,
        pauseHoldMs = -1L,
        isCustom = true
    );
}
