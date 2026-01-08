package com.rohit.voicepause.audio

enum class AudioProfile(
    val displayName: String,
    val vadAggressiveness: Int,
    val minSpeechMs: Long,
    val silenceDelayMs: Long
) {
    QUIET(
        displayName = "Quiet Room",
        vadAggressiveness = 0,
        minSpeechMs = 250,
        silenceDelayMs = 500
    ),

    BUSY(
        displayName = "Busy Room",
        vadAggressiveness = 2,
        minSpeechMs = 400,
        silenceDelayMs = 800
    ),

    TRAFFIC(
        displayName = "Traffic / Outdoors",
        vadAggressiveness = 3,
        minSpeechMs = 700,
        silenceDelayMs = 1200
    );
}
