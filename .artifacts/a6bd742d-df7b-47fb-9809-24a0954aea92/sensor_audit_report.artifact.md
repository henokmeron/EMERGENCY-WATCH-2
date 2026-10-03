# Real Galaxy Watch4 (SM-R875F) Sensor Audit Report

This document presents the complete audit of the real sensor pipeline on the physical Samsung Galaxy Watch4 (`SM-R875F`), tracing every sensor from physical hardware through the Samsung Health Sensor SDK (`HealthTrackingService`), `HealthTracker`, data point callbacks, internal parser, Room database, telemetry payload, backend API, and monitoring dashboard.

---

## Sensor Audit Table

| Sensor | Physical Watch Support | Permission | Tracker | Real Callback | Room | Telemetry | Backend | Dashboard |
|---|---|---|---|---|---|---|---|---|
| **Heart Rate (`heart_rate`)** | YES | `READ_HEART_RATE`, `BODY_SENSORS` | `HEART_RATE_CONTINUOUS` | YES | YES | YES (`Int?`) | YES (HTTP 200) | YES |
| **Accelerometer / Movement (`movement`, `fall_*`)** | YES | `BODY_SENSORS` | `ACCELEROMETER_CONTINUOUS` | YES | YES | YES (`Double`) | YES (HTTP 200) | YES |
| **Contact Status (`contact_status`)** | YES | `BODY_SENSORS` | Derived from HR status code (-3 off-wrist) | YES | YES | YES (`Boolean`) | YES (HTTP 200) | YES |
| **SpO2 (`spo2`)** | NO (Not continuous) | N/A | None (Not in SDK v1.4.1 continuous list) | NO | YES (`null`) | YES (`null`) | YES (Accepted) | No live data (shows baseline/demo) |
| **ECG (`ecg`)** | NO (Spot check only) | Medical / Health Services | None | NO | NO | NO | N/A | NO |
| **Skin Temperature** | NO | N/A | None | NO | NO | NO | N/A | NO |
| **BIA (Body Composition)** | NO (Requires two-finger touch) | N/A | None | NO | NO | NO | N/A | NO |
| **EDA (Electrodermal Activity)** | NO | N/A | None | NO | NO | NO | N/A | NO |

---

## Pipeline Tracing Analysis (A through E)

### A. What sensors are actually producing data on the physical Watch4?
- **Heart Rate (`heart_rate`)**: Actively produced via `HEART_RATE_CONTINUOUS` tracker when worn on the wrist (`status = 1` or valid BPM, status `-3` when off-wrist).
- **Accelerometer (`movement`, `fall_*`)**: Actively produced via `ACCELEROMETER_CONTINUOUS` tracker, converted via `SamsungAccelConverter` into $m/s^2$, and processed via `MovementProcessor`.
- **Contact Status (`contact_status`)**: Derived reliably from HR status codes (`-3` = detached / off wrist).

### B. What data is being produced by the watch app but lost before telemetry?
- **None.** All sensor data produced by active trackers (`heart_rate`, `contact_status`, `movement`, `fall_detected`, `fall_confidence`) is successfully passed through `SensorData`, sampled via `TelemetrySampler`, and persisted into Room (`TelemetryEntity`).

### C. What data is successfully included in telemetry?
- `reading_id`, `recorded_at`, `heart_rate`, `contact_status`, `spo2` (`null`), `movement`, `fall_detected`, `fall_confidence`, and `location` (`null`).
- Outgoing JSON payload:
  ```json
  {
    "readings": [
      {
        "reading_id": "...",
        "recorded_at": "2025-02-23T...",
        "heart_rate": 72,
        "contact_status": true,
        "spo2": null,
        "movement": 0.05,
        "fall_detected": false,
        "fall_confidence": null,
        "location": null
      }
    ]
  }
  ```

### D. What data reaches the backend?
- All fields in `ReadingDto` reach `POST /api/public/watch/telemetry`.
- The backend responds with `HTTP 200 OK` and `accepted=N`, acknowledging the batch.

### E. What data the backend accepts but the dashboard doesn't display?
- **SpO2 (`spo2: null`)**: Because SpO2 is not measured continuously by this watch app today (`spo2` is always `null`), the live telemetry stream transmits `spo2: null`.
- As noted in `AGENTS.md`, the Lovable dashboard often displays seeded baseline/demo values (e.g., 97% SpO2) for demonstration wards, which can create a mismatch against the physical watch's unmeasured SpO2 (`--`). The backend successfully accepts the telemetry payload containing `spo2: null`, but the web dashboard layer displays static/demo fallbacks for unmeasured fields unless updated on the Lovable frontend.

---

## Required Actions on Physical Watch4

1. **Permissions**: Ensure `BODY_SENSORS` and `READ_HEART_RATE` permissions are granted to the app in watch Settings > Apps > Permissions.
2. **Health Sensor Service Developer Mode** (if SDK policy errors occur):
   - Settings → Apps → Health Sensor Service (or Health Platform)
   - Tap title ~10 times to enable **Developer Mode ON**.
3. **On-Wrist Placement**: Wear the watch snugly on the wrist so `contact_status` reports `true` and live heart rate values are captured.
