
# 📄 Application Flow Documentation

---

## 1. Entry Points

### Primary Entry Points

* **Direct App Launch**:
  User opens **VoicePause** → lands on **Control Dashboard**

  * Displays:

    * System status (Active / Inactive)
    * Start/Stop monitoring button
    * Sensitivity indicator (future)

---

* **Foreground Service Activation**:

  * Triggered via:

    * Start button in app
    * (Future) auto-start on device boot
  * Behavior:

    * Begins real-time audio monitoring
    * Shows persistent notification

---

* **Notification Entry**:

  * User taps persistent notification
  * Opens:

    * Dashboard screen

---

* **Deep Links**:

  Not applicable (no external navigation required)

---

* **OAuth/Social Login**:

  Not applicable (no authentication required)

---

### Secondary Entry Points

* **Service Restart (System Triggered)**:

  * OS restarts foreground service
  * App resumes monitoring state

---

* **Boot Trigger (Future)**:

  * Device restart → auto-enable monitoring

---

## 2. Core User Flows

---

### Flow 1: Speech-Based Media Control (Core Flow)

**Goal**: Automatically pause and resume media based on user speech
**Entry Point**: Foreground service active
**Frequency**: Continuous

---

#### Happy Path

1. **System State: Passive Monitoring**

   * Elements:

     * AudioRecord stream (earbud mic)
     * Buffer processing (10–30ms chunks)
   * Behavior:

     * Continuous audio capture

---

2. **System Action: VAD Detection**

   * Input:

     * Audio chunk
   * Output:

     * Speech candidate (TRUE / FALSE)
   * Trigger:

     * Speech-like signal detected

---

3. **System Action: ML Speech Validation**

   * Input:

     * Candidate speech segment

   * Processing:

     * Run ML model (e.g., YAMNet)

   * Output:

     * Speech confidence score

   * Decision:

     * If confidence > threshold → valid speech

---

4. **System Action: Temporal Smoothing**

   * Logic:

     * Speech must persist ≥300–500ms
   * Purpose:

     * Prevent false triggers

---

5. **System Action: Pause Media**

   * Action:

     * Request audio focus
     * Pause current media session
   * Feedback:

     * Optional UI / notification update

---

6. **System State: Active Speech**

   * Behavior:

     * Continue monitoring
     * Do NOT re-trigger pause

---

7. **System Action: Silence Detection**

   * Logic:

     * No speech for ≥1–2 seconds

---

8. **System Action: Resume Media**

   * Action:

     * Restore audio focus
     * Resume playback

---

#### Error States

* **VAD Failure**

  * Missed speech → no pause
  * Mitigation: adjust aggressiveness

---

* **ML Misclassification**

  * Noise classified as speech → false pause
  * Mitigation: threshold tuning

---

* **Audio Focus Failure**

  * Media not paused
  * Mitigation: retry / fallback handling

---

* **Microphone Access Denied**

  * Monitoring fails
  * Prompt on next app open

---

#### Edge Cases

* Very short speech (<300ms) → ignored
* Loud background noise → filtered by ML
* Continuous talking → no repeated triggers
* Earbud mic disconnection → fallback or stop

---

#### Exit Points

* Success: Seamless pause/resume
* Failure: Detection error
* Manual: User stops service

---

---

### Flow 2: Service Lifecycle Management

**Goal**: Maintain reliable background execution
**Entry Point**: User starts monitoring
**Frequency**: Moderate

---

#### Happy Path

1. **User Action: Start Monitoring**

   * UI:

     * Toggle button
   * Trigger:

     * Start foreground service

---

2. **System Action: Initialize Audio Pipeline**

   * Components:

     * AudioRecord
     * VAD module
     * ML model (lazy-loaded)

---

3. **System State: Active Monitoring**

   * Notification:

     * Persistent (“VoicePause Active”)

---

4. **User Action: Stop Monitoring**

   * Trigger:

     * Stop service

---

5. **System Action: Cleanup**

   * Release:

     * Microphone
     * Threads
     * Buffers

---

#### Error States

* Service killed by OS → auto-restart attempt
* Resource lock failure → retry initialization

---

#### Edge Cases

* App minimized → service continues
* Low battery → optional throttling

---

#### Exit Points

* Service stopped manually
* System termination

---

---

### Flow 3: (Future) User-Specific Voice Detection

**Goal**: Trigger pause only for the user’s voice
**Entry Point**: After speech validation
**Frequency**: Conditional

---

#### Happy Path

1. **System Input: Valid Speech Segment**

---

2. **System Action: Speaker Embedding Extraction**

   * Model:

     * Resemblyzer / ECAPA
   * Output:

     * Embedding vector

---

3. **System Action: Speaker Matching**

   * Compare:

     * Live embedding vs stored user embedding
   * Metric:

     * Cosine similarity

---

4. **Decision**

   * If similarity > threshold → user detected
   * Else → ignore

---

5. **System Action: Trigger Pause**

---

#### Error States

* Poor audio quality → low similarity
* Model drift → inaccurate matching

---

#### Edge Cases

* Whispering → lower confidence
* Similar voice → false match

---

#### Exit Points

* Match success → pause
* Match failure → ignore

---

---

## 3. Navigation Map

### Primary Navigation

* Dashboard
  → Start/Stop Monitoring
  → Settings (future)

---

### Navigation Rules

* **Authentication Required**: Not applicable

* **Redirect Logic**:

  * If service active → show active state
  * Else → idle state

* **Back Button Behavior**:

  * Service continues in background

---

## 4. Screen Inventory

---

### Screen: Dashboard

* **Route**: `/dashboard`
* **Access**: Public
* **Purpose**: Control monitoring system
* **Key Elements**:

  * Start/Stop button
  * System status
  * Notification access
* **Actions Available**:

  * Start → activates service
  * Stop → deactivates service
* **State Variants**:

  * Idle
  * Active

---

---

## 5. Interaction Patterns

### Pattern: Continuous Audio Streaming

* Chunk-based processing (10–30ms)
* Real-time inference

---

### Pattern: Event-Driven ML

* ML runs only after VAD trigger
* Optimized for performance

---

### Pattern: Audio Focus Control

* Request focus → pause media
* Release focus → resume media

---

---

## 6. Decision Points

### Decision: Speech Detection

* If VAD TRUE → proceed
* Else → ignore

---

### Decision: Speech Validation

* If ML confidence > threshold → valid
* Else → ignore

---

### Decision: Silence Detection

* If silence duration met → resume

---

---

## 7. Error Handling Flows

### Microphone Failure

* Display: Prompt on next app open
* Action: Disable monitoring

---

### Model Failure

* Fallback: VAD-only mode

---

### Audio Focus Conflict

* Retry request
* Fallback: partial control

---

---

## 8. Responsive Behavior

### Mobile-Specific Flows

* Primary platform
* Background-first architecture
* Minimal UI interaction

---

### Desktop-Specific Flows

* Not applicable

---

## 9. Animation & Transitions

### Page Transitions

* Minimal (utility app)

---

### Micro-interactions

* Start/Stop toggle feedback
* Notification updates
* Subtle status changes

---

This flow prioritizes:

* **Low latency**
* **High precision**
* **Battery efficiency**
* **Zero-friction UX**


