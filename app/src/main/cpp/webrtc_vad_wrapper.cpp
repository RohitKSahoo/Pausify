#include <jni.h>
#include <android/log.h>
#include <memory>
#include <cstring>

// WebRTC VAD includes (you'll need to link against WebRTC libraries)
extern "C" {
    // WebRTC VAD C interface declarations
    // Note: In a real implementation, you'd include the actual WebRTC headers
    // For now, we'll provide stub implementations that demonstrate the interface
    
    typedef struct WebRtcVadInst VadInst;
    
    // WebRTC VAD function declarations (normally from webrtc/common_audio/vad/include/webrtc_vad.h)
    VadInst* WebRtcVad_Create();
    void WebRtcVad_Free(VadInst* handle);
    int WebRtcVad_Init(VadInst* handle);
    int WebRtcVad_set_mode(VadInst* handle, int mode);
    int WebRtcVad_Process(VadInst* handle, int fs, const int16_t* audio_frame, size_t frame_length);
}

#define LOG_TAG "WebRtcVadWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// JNI method implementations
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_rohit_voicepause_audio_WebRtcVadWrapper_nativeCreateVad(JNIEnv *env, jobject thiz) {
    VadInst* vad_handle = WebRtcVad_Create();
    if (vad_handle == nullptr) {
        LOGE("Failed to create WebRTC VAD instance");
        return 0;
    }
    
    LOGD("WebRTC VAD instance created successfully");
    return reinterpret_cast<jlong>(vad_handle);
}

JNIEXPORT jint JNICALL
Java_com_rohit_voicepause_audio_WebRtcVadWrapper_nativeInitVad(JNIEnv *env, jobject thiz, 
                                                               jlong vad_handle, jint sample_rate) {
    if (vad_handle == 0) {
        LOGE("Invalid VAD handle");
        return -1;
    }
    
    VadInst* vad = reinterpret_cast<VadInst*>(vad_handle);
    
    // Initialize the VAD
    int result = WebRtcVad_Init(vad);
    if (result != 0) {
        LOGE("Failed to initialize WebRTC VAD: %d", result);
        return result;
    }
    
    LOGD("WebRTC VAD initialized with sample rate: %d Hz", sample_rate);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_rohit_voicepause_audio_WebRtcVadWrapper_nativeSetAggressiveness(JNIEnv *env, jobject thiz,
                                                                         jlong vad_handle, jint aggressiveness) {
    if (vad_handle == 0) {
        LOGE("Invalid VAD handle");
        return -1;
    }
    
    if (aggressiveness < 0 || aggressiveness > 3) {
        LOGE("Invalid aggressiveness level: %d (must be 0-3)", aggressiveness);
        return -1;
    }
    
    VadInst* vad = reinterpret_cast<VadInst*>(vad_handle);
    
    int result = WebRtcVad_set_mode(vad, aggressiveness);
    if (result != 0) {
        LOGE("Failed to set VAD aggressiveness to %d: %d", aggressiveness, result);
        return result;
    }
    
    LOGD("VAD aggressiveness set to: %d", aggressiveness);
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_rohit_voicepause_audio_WebRtcVadWrapper_nativeProcessFrame(JNIEnv *env, jobject thiz,
                                                                    jlong vad_handle, jshortArray audio_frame, jint frame_size) {
    if (vad_handle == 0) {
        LOGE("Invalid VAD handle");
        return -1;
    }
    
    if (audio_frame == nullptr) {
        LOGE("Audio frame is null");
        return -1;
    }
    
    VadInst* vad = reinterpret_cast<VadInst*>(vad_handle);
    
    // Get audio data from Java array
    jshort* audio_data = env->GetShortArrayElements(audio_frame, nullptr);
    if (audio_data == nullptr) {
        LOGE("Failed to get audio data from Java array");
        return -1;
    }
    
    // Determine sample rate based on frame size
    // 20ms frames: 160 samples @ 8kHz, 320 @ 16kHz, 640 @ 32kHz, 960 @ 48kHz
    int sample_rate;
    switch (frame_size) {
        case 160: sample_rate = 8000; break;
        case 320: sample_rate = 16000; break;
        case 640: sample_rate = 32000; break;
        case 960: sample_rate = 48000; break;
        default:
            LOGE("Unsupported frame size: %d", frame_size);
            env->ReleaseShortArrayElements(audio_frame, audio_data, JNI_ABORT);
            return -1;
    }
    
    // Process the audio frame through WebRTC VAD
    int vad_result = WebRtcVad_Process(vad, sample_rate, 
                                       reinterpret_cast<const int16_t*>(audio_data), 
                                       static_cast<size_t>(frame_size));
    
    // Release the audio data
    env->ReleaseShortArrayElements(audio_frame, audio_data, JNI_ABORT);
    
    if (vad_result < 0) {
        LOGE("WebRTC VAD processing failed: %d", vad_result);
        return vad_result;
    }
    
    // Return VAD result: 1 = speech, 0 = no speech
    return vad_result;
}

JNIEXPORT jint JNICALL
Java_com_rohit_voicepause_audio_WebRtcVadWrapper_nativeDestroyVad(JNIEnv *env, jobject thiz, 
                                                                  jlong vad_handle) {
    if (vad_handle == 0) {
        LOGD("VAD handle already null, nothing to destroy");
        return 0;
    }
    
    VadInst* vad = reinterpret_cast<VadInst*>(vad_handle);
    WebRtcVad_Free(vad);
    
    LOGD("WebRTC VAD instance destroyed");
    return 0;
}

} // extern "C"

// Stub implementations for WebRTC VAD functions
// In a real implementation, you would link against the actual WebRTC library
extern "C" {

struct WebRtcVadInst {
    int mode;
    int sample_rate;
    bool initialized;
    
    WebRtcVadInst() : mode(1), sample_rate(16000), initialized(false) {}
};

VadInst* WebRtcVad_Create() {
    return new WebRtcVadInst();
}

void WebRtcVad_Free(VadInst* handle) {
    delete handle;
}

int WebRtcVad_Init(VadInst* handle) {
    if (handle == nullptr) return -1;
    handle->initialized = true;
    return 0;
}

int WebRtcVad_set_mode(VadInst* handle, int mode) {
    if (handle == nullptr || !handle->initialized) return -1;
    if (mode < 0 || mode > 3) return -1;
    handle->mode = mode;
    return 0;
}

int WebRtcVad_Process(VadInst* handle, int fs, const int16_t* audio_frame, size_t frame_length) {
    if (handle == nullptr || !handle->initialized || audio_frame == nullptr) {
        return -1;
    }
    
    // Stub implementation: Simple energy-based detection
    // In a real implementation, this would be the actual WebRTC VAD algorithm
    long long energy = 0;
    for (size_t i = 0; i < frame_length; i++) {
        energy += (long long)audio_frame[i] * audio_frame[i];
    }
    
    // Simple threshold based on aggressiveness mode
    long long threshold;
    switch (handle->mode) {
        case 0: threshold = 1000000LL; break;   // Least aggressive
        case 1: threshold = 2000000LL; break;   // Low bitrate
        case 2: threshold = 4000000LL; break;   // Aggressive
        case 3: threshold = 8000000LL; break;   // Very aggressive
        default: threshold = 2000000LL; break;
    }
    
    return (energy > threshold) ? 1 : 0;
}

} // extern "C"