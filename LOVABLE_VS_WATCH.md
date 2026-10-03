# Lovable dashboard vs physical watch — agent brief

## Problem
The monitoring site card for **Priya Raman** shows values like HR ~73, SpO2 97%, accelerometer Active, while the **physical Galaxy Watch4** often shows OFF WRIST / HR `--` / SpO2 not measured. That mismatch means the **website is rendering baseline or demo data**, not the latest watch telemetry.

## What the Android watch already does
- Uploads real telemetry to `https://med-eye.lovable.app/api/public/watch/telemetry`
- Tags physical builds: `app_version=1.0.0-PHYSICAL-DEV`, heartbeat `sensors.physical_watch=true`
- Watch UI labels itself **PHYSICAL WATCH · DEV** and never invents SpO2/HR

## What Lovable must do
1. Mark seeded/simulated patients as **FAKE USER** / **DEMO**.
2. Mark the paired physical device as **PHYSICAL WATCH · DEV** (different color/badge).
3. Bind the card’s HR / SpO2 / skin contact / chart to the **latest accepted telemetry** for that `patient_id`/`device_id`.
4. If `heart_rate` is null → show `--` (not baseline 70/73).
5. If SpO2 not in reading → show `--` (not 97).
6. If `contact_status=false` → **Detached / OFF WRIST** (already partially correct).
7. If `contact_status=true` and `heart_rate` is present → show that HR as live.
8. Battery: use `battery_level` from the **latest heartbeat**. The watch sends heartbeat every **60 s**.
9. Watch contact fix (Oct 1): off-wrist defaults to `contact_status=false`. Off-body sensor is authoritative; valid HR only *confirms* on-wrist. If off-body is stuck ON with no HR for 45s (desk/charger), watch sends `contact_status=false`. Lovable should keep mapping `false` → OFF WRIST / Detached — **do not need option 2**.

## Do not
- Invent new watch APIs from the Android side for labels
- Treat baseline config as live vitals
