# ⏸️ Pausify (v1.0.0)

Pausify is an Android app that automatically pauses your media when you speak and resumes it when you stop.

It creates a seamless, hands-free experience by:
- Detecting when you start speaking
- Automatically pausing active media (Spotify, YouTube, etc.)
- Resuming playback once you are done talking

All processing happens entirely on-device for maximum privacy and low latency.

### 🎧 **For when you are vibing to your music, but life demands a quick conversation.**

---

## 📱 Screenshots

<table align="center">
  <tr>
    <td align="center">
      <img src="assets/Dashboard.png" width="250"/><br/>
      <b>Control Dashboard</b>
    </td>
    <td width="20"></td>
    <td align="center">
      <img src="assets/Settings.png" width="250"/><br/>
      <b>Settings</b>
    </td>
  </tr>
</table>

---

## ⭐ Features

- 🎙️ **Real-Time Speech Detection**  
  Detects speech onset within 100–200ms using a low-latency VAD layer.

- 🧠 **ML-Based Validation**  
  Uses YAMNet (TensorFlow Lite) to distinguish speech from noise, reducing false triggers to <3%.

- ⏯️ **Intelligent Playback Control**  
  Smoothly pauses and resumes media without rapid toggling.

- 📱 **Foreground Service**  
  Operates reliably in the background with a persistent notification.

- 🔒 **Privacy Friendly**  
  All audio processing and inference happen locally on your device.

- 📊 **Status Dashboard**  
  Shows the current system state (Active / Inactive).

---

## 🛠️ Tech Stack

- Kotlin
- Jetpack Compose
- TensorFlow Lite
- WebRTC VAD (C++/JNI)
- Coroutines & Flow

### APIs Used
- Android AudioRecord API
- Android AudioManager API

---

## 🏗️ Architecture

- Pipeline-based processing (Audio → VAD → ML → Action)
- Foreground Service for continuous monitoring

---

## 🚀 Getting Started

### Requirements

- Android 10.0 (API 29) or higher
- Microphone permission
- Notification permission

---

### Installation

👉 [Download APK](https://github.com/RohitKSahoo/Pausify/releases)

---

## 👤 Author

Rohit K Sahoo  
GitHub: https://github.com/RohitKSahoo

---

## 📄 License

This project is licensed under the MIT License.

---

## 🧾 Summary

Pausify helps you automatically control media playback based on your voice, eliminating the need to manually pause when speaking.