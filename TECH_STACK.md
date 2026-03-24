
# Technology Stack Documentation

## 1. Stack Overview

**Last Updated**: 24 March 2026
**Version**: 1.0

### Architecture Pattern

* **Type**: Edge AI (On-device ML + Mobile Client)
* **Pattern**: Real-time streaming inference (pipeline-based)
* **Deployment**: Fully on-device (no backend dependency for core logic)

---

## 2. Frontend Stack

### Core Framework

* **Framework**: Android Native (Kotlin)
* **Version**: Latest stable (Android SDK 34+)
* **Reason**:

  * Low-level access to microphone (AudioRecord)
  * Required for real-time processing
  * Precise control over audio pipeline and threading
* **Documentation**: [https://developer.android.com](https://developer.android.com)
* **License**: Apache 2.0

---

### UI Layer

* **Framework**: Jetpack Compose
* **Reason**:

  * Minimal UI requirements
  * Fast iteration for control panel
  * Clean state-driven updates

---

### State Management

* **Approach**: ViewModel + StateFlow
* **Reason**:

  * Lifecycle-aware
  * Stable under foreground service execution
  * Reactive updates for system state

---

### Background Processing

* **Tools**:

  * Foreground Service (core requirement)
  * Kotlin Coroutines (real-time processing)

* **Reason**:

  * Continuous audio monitoring
  * Non-blocking audio + ML pipeline
  * Survives OS background restrictions

---

### Audio Processing

* **API**: AudioRecord
* **Reason**:

  * Low-level control over audio stream
  * Required for real-time chunk processing (10–30ms)
  * Better than MediaRecorder for DSP/ML

---

### Audio Control (Media)

* **API**: AudioManager + AudioFocusRequest
* **Reason**:

  * Pause/resume external media apps
  * Fine-grained control over playback behavior

---

### DSP (Digital Signal Processing)

* **Techniques**:

  * Frame chunking (10–30ms)
  * RMS / energy detection
  * Optional noise suppression (RNNoise)

* **Reason**:

  * Preprocessing before ML inference
  * Improves signal quality

---

## 3. ML / AI Stack

### VAD (Voice Activity Detection)

* **Library**: WebRTC VAD (C++ / JNI)
* **Reason**:

  * Extremely fast and lightweight
  * Industry-standard for real-time speech detection
  * Runs efficiently on mobile CPU

---

### Speech Classification Model

* **Model**: YAMNet (TensorFlow Lite)
* **Framework**: TensorFlow Lite
* **Reason**:

  * Pretrained on AudioSet (high coverage)
  * Can distinguish speech vs non-speech reliably
  * No need to train custom model initially

---

### Inference Engine

* **Framework**: TensorFlow Lite
* **Reason**:

  * Optimized for mobile
  * Low latency inference
  * Hardware acceleration support

---

### (Future) Speaker Recognition

* **Model**: Resemblyzer / ECAPA-TDNN (converted to ONNX/TFLite)
* **Framework**: ONNX Runtime Mobile / TFLite
* **Reason**:

  * Generates speaker embeddings
  * Enables user-specific detection

---

### Feature Extraction (if custom ML used)

* **Techniques**:

  * MFCC
  * Log-mel spectrogram

* **Libraries**:

  * Custom DSP or TFLite preprocessing

---

## 4. Backend Stack (Minimal / Optional)

### Platform

* **Service**: Firebase (Optional)

* **Components Used**:

  * Firebase Analytics
  * Firebase Crashlytics

* **Reason**:

  * Monitor performance
  * Track false positives / usage

---

### Database

* **Primary**: Local (Room / SQLite)
* **Reason**:

  * Store session logs
  * Store user voice embeddings (future)
  * No dependency on network

---

### API Layer

* **Type**: None (MVP)
* **Reason**:

  * Core logic is on-device
  * Eliminates latency + infra overhead

---

## 5. DevOps & Infrastructure

### Version Control

* **System**: Git
* **Platform**: GitHub
* **Branch Strategy**:

  * main
  * dev
  * feature/*

---

### CI/CD

* **Platform**: GitHub Actions
* **Workflows**:

  * Build APK
  * Run lint + tests
  * Static analysis checks

---

### Monitoring

* **Crash Reporting**: Firebase Crashlytics
* **Analytics**: Firebase Analytics (optional)
* **Performance**: Android Profiler

---

### Testing

* **Unit Testing**: JUnit
* **UI Testing**: Espresso
* **Audio Testing**:

  * Simulated audio input
  * Real-world scenario testing

---

## 6. Development Tools

### Code Quality

* **Linter**: ktlint
* **Formatter**: ktfmt
* **Static Analysis**: Detekt

---

### IDE Recommendations

* **Editor**: Android Studio
* **Plugins**:

  * Kotlin
  * TFLite Support
  * Jetpack Compose Tools

---

## 7. Environment Variables

### Required Variables

```bash
# App Config
APP_ENV="development"

# Audio Processing
AUDIO_SAMPLE_RATE=16000
AUDIO_CHUNK_SIZE=20   # ms

# Detection Thresholds
VAD_AGGRESSIVENESS=2
ML_CONFIDENCE_THRESHOLD=0.7
SPEECH_DURATION_THRESHOLD=300
SILENCE_DURATION_THRESHOLD=1200

# Feature Flags
ENABLE_ML=true
ENABLE_SPEAKER_MODEL=false
```

---

## Strategic Notes (Important)

* **You intentionally avoided**:

  * Heavy backend systems
  * Continuous cloud inference
  * Complex distributed architecture

→ This is correct. Your system is **latency-critical**, not data-heavy.

---

* **Your stack is optimized for**:

  * Real-time performance
  * Battery efficiency
  * On-device privacy
  * Deterministic behavior

---

* **Upgrade Path (Post-MVP)**:

  * Replace YAMNet with custom lightweight model
  * Add speaker recognition (user-specific detection)
  * Introduce adaptive thresholds (environment-aware)
  * Optional cloud analytics for tuning


