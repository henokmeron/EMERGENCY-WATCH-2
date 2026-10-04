# Shared Monitoring Platform Contract

This repository remains named `EMERGENCY-WATCH-2`. It is the device/edge layer.

## Responsibilities
- Acquire genuine sensor data.
- Preserve null/unavailable values; never invent measurements.
- Buffer telemetry locally when transport is unavailable.
- Authenticate the device and rotate credentials.
- Send canonical telemetry to the monitoring backend.
- Report device/monitoring health separately from patient physiology.
- Keep provider-specific code behind adapters.

## Canonical telemetry
All future wearable adapters should map into these concepts when supported:
- identity: patient_id, device_id, provider, model, firmware
- time: recorded_at, received_at
- physiology: heart_rate, spo2, respiration_rate, skin_temperature_c, core_temperature_c
- cardiac: hrv_ms, rr_interval_ms, ecg_available
- activity: movement, steps, fall_detected, fall_confidence
- wearing: contact_status
- device health: battery_pct, connectivity, signal_quality
- location: latitude, longitude, accuracy_m

Unsupported measurements MUST remain null/unknown, not be backfilled from baseline/demo data.

## Monitoring health
Patient state and monitoring state are independent:
- patient state: stable / caution / critical
- monitoring state: healthy / degraded / off-body / stale / disconnected / low-battery / sensor-error

## Safety boundary
The wearable and backend may detect events and risk signals. They must not claim a diagnosis. Human escalation remains the operational safety layer.

## Provider strategy
Galaxy Watch is a development provider. Corsano is an integration target. Future OEM/cellular devices must be addable without changing the canonical detection contract.
