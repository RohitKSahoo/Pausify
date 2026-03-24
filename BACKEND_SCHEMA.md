
# 📄 Backend Schema (VoicePause)

*(Adapted to on-device, real-time audio intelligence architecture)*

---

## 1. Architecture Overview

### System Architecture

* **Pattern**: On-device, real-time inference (edge AI)
* **Backend Role**: Minimal (analytics + optional config sync)
* **Data Flow**:

```
Mic Input → On-device Processing (VAD + ML) → Media Control
                        ↓
                  Local Logging / Analytics
```

*(Optional future)*:

```
Device → Firebase (analytics/config) → Device
```

---

### Caching Strategy

* Primary:

  * In-memory buffers (real-time audio)
* Secondary:

  * Local storage (Room / SharedPreferences)

---

## 2. Database Schema

### Database

* **Primary**: Local (Room DB / SQLite)
* **Optional Remote**: Firebase (analytics only)
* **Naming Convention**: camelCase
* **Timestamps**: All logs include `createdAt`

---

## 3. Collections / Tables & Relationships

---

### Table: sessions

**Purpose**: Track monitoring sessions

| Field     | Type      | Constraints           | Description      |
| --------- | --------- | --------------------- | ---------------- |
| id        | string    | PRIMARY KEY           | Session ID       |
| startTime | timestamp | NOT NULL              | Monitoring start |
| endTime   | timestamp | NULL                  | Monitoring end   |
| status    | string    | ENUM(active, stopped) | Session state    |
| createdAt | timestamp | DEFAULT now           | Creation time    |

**Indexes**:

* status
* startTime

---

### Table: detection_events

**Purpose**: Log speech detection decisions

| Field      | Type      | Constraints                                    | Description      |
| ---------- | --------- | ---------------------------------------------- | ---------------- |
| id         | string    | PRIMARY KEY                                    | Event ID         |
| sessionId  | string    | FOREIGN KEY → sessions.id                      |                  |
| eventType  | string    | ENUM(speech_start, speech_end, false_positive) |                  |
| confidence | float     | NULL                                           | ML confidence    |
| source     | string    | ENUM(VAD, ML, Speaker)                         | Detection source |
| timestamp  | timestamp | NOT NULL                                       | Event time       |
| createdAt  | timestamp | DEFAULT now                                    |                  |

**Indexes**:

* sessionId
* timestamp

---

### Table: audio_metrics

**Purpose**: Store aggregated audio stats (not raw audio)

| Field        | Type      | Description       |
| ------------ | --------- | ----------------- |
| id           | string    | Primary key       |
| sessionId    | string    | Reference         |
| avgAmplitude | float     | Signal level      |
| noiseLevel   | float     | Background noise  |
| speechRatio  | float     | % speech detected |
| createdAt    | timestamp |                   |

---

### Table: user_voice_profile (Future - Plan 2)

**Purpose**: Store speaker embedding

| Field       | Type      | Description      |
| ----------- | --------- | ---------------- |
| id          | string    | Primary key      |
| embedding   | blob      | Vector data      |
| sampleCount | number    | Training samples |
| createdAt   | timestamp |                  |

---

## Relationships Summary

* sessions → detection_events (one-to-many)
* sessions → audio_metrics (one-to-many)
* user_voice_profile → used during inference

---

## 4. API Endpoints (Optional / Future)

*(Only relevant if you add cloud sync or analytics)*

---

### POST /analytics/session

**Purpose**: Upload session summary

```json
{
  "duration": 1200,
  "falseTriggers": 2,
  "avgLatency": 250
}
```

---

### POST /analytics/event

**Purpose**: Log detection event

```json
{
  "type": "speech_start",
  "confidence": 0.82
}
```

---

### GET /config

**Purpose**: Fetch dynamic thresholds

**Response**:

```json
{
  "vadThreshold": 0.6,
  "mlThreshold": 0.7,
  "silenceDuration": 1200
}
```

---

## 5. Authentication & Authorization

### Strategy

* Not required (local-first system)

---

### Future

* Anonymous analytics ID (if backend added)

---

## 6. Data Validation Rules

### Audio Input

* Sample rate: 16kHz (recommended)
* Mono channel
* Chunk size: 10–30ms

---

### ML Output

* Confidence range: 0.0–1.0
* Threshold configurable

---

### Speaker Embedding (Future)

* Fixed vector size (e.g., 256 / 512)
* Normalized vectors

---

## 7. Error Handling

### Error Format (Internal)

```json
{
  "error": {
    "code": "AUDIO_INIT_FAILED",
    "message": "Microphone initialization failed"
  }
}
```

---

### Error Codes

* AUDIO_INIT_FAILED
* MODEL_LOAD_FAILED
* INFERENCE_ERROR
* AUDIO_FOCUS_FAILED

---

## 8. Caching Strategy

### Layers

* In-memory:

  * Audio buffers
  * Recent inference results

* Persistent:

  * Session logs
  * User voice embedding

---

### Invalidation

* On session end → flush buffers
* On app restart → reload configs

---

## 9. Rate Limiting

### Constraints

* ML inference → only after VAD trigger
* Audio processing → fixed real-time stream

---

### Optimization

* Skip ML on silence
* Batch small chunks if needed

---

## 10. Database Migrations

### Strategy

* Room versioning
* Additive schema updates

---

## 11. Backup & Recovery

### Strategy

* Local-only (MVP)
* Optional cloud sync later

---

## 12. API Versioning

* **Current Version**: v1
* **Strategy**:

  * Only relevant if backend introduced

---

## Final Architecture Insight

You are intentionally building:

* **Edge-first system (on-device intelligence)**
* **Zero backend dependency for core functionality**
* **Optional backend for analytics and tuning**

This is correct because:

* Real-time constraints
* Privacy requirements
* Battery + latency optimization



