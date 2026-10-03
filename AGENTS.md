# AGENTS.md — Emergency Watch (Android Studio / Cursor)

**Authoritative project folder:**  
`Desktop\EMERGENCY WATCH 2`  
Do **not** use `EMERGENCY WATCH 1`.

Modules in **one** Gradle project:
- `:wear` — Galaxy Watch app (primary telemetry path)
- `:mobile` — phone companion (Data Layer status / pair assist only)

Backend (do not invent endpoints): `https://med-eye.lovable.app`

---

## Critical truth: Watch vs Lovable dashboard mismatch

### What the PHYSICAL Galaxy Watch (SM-R875F) actually sends
- Live fields only when sensors work: `heart_rate` (nullable), `contact_status`, `movement`, `fall_*`
- **SpO2 is NOT measured** by this watch app today → always `null` / `--` on the watch UI
- If watch shows **OFF WRIST** / **HR `--`**, that is the real state

### What Lovable often shows (FAKE / BASELINE / DEMO)
Many ward cards (including some that look “live”) display:
- Baseline HR (e.g. **70 / 73**)
- Baseline SpO2 (e.g. **97%**)
- Flat sparkline charts
- Seeded “fake users” for demo wards

**If Lovable shows SpO2 97% while the watch shows `SpO2: -- (not measured)`, Lovable is NOT showing this watch’s live sample.**  
It is showing baseline/demo UI, not the physical watch stream.

### Confirmed live path (already verified on SM-R875F)
```
Watch → POST /api/public/watch/telemetry → HTTP 200 accepted=N
Patient example: Priya Raman (patient_id from /config)
```
When contact was false, backend returned alerts: `"No skin contact"`.

---

## Labels agents MUST preserve

### On the watch UI (`:wear`)
- Banner: **`PHYSICAL WATCH · DEV`** (real device) or **`EMULATOR`**
- Show live HR or `--` (never invent numbers)
- Show `SpO2: -- (not measured)`
- Settings / Status must show: patient name, patient id, device id, contact, battery, sensor source, last sync

### In existing API fields (no new endpoints)
Heartbeat / pair already tag physical builds:
- `app_version`: `1.0.0-PHYSICAL-DEV` vs `1.0.0-EMULATOR`
- `PairRequest.model`: includes `PHYSICAL-DEV`
- `HeartbeatRequest.sensors`: includes `physical_watch`, `dev_build`, `emulator`

### On Lovable (web — NOT in this Android repo)
Agents working on the **Lovable** project should:
1. Label seed/demo patients **`FAKE USER`** / **`DEMO`**
2. Label devices with `app_version` containing `PHYSICAL-DEV` or `sensors.physical_watch=true` as **`PHYSICAL WATCH · DEV`** (highlight differently)
3. Never paint baseline HR/SpO2 as if they were the latest telemetry when latest reading has `heart_rate=null` / no SpO2
4. Prefer last accepted telemetry reading for the paired `device_id` / `patient_id`
5. Show **SKIN CONTACT Detached** when `contact_status=false` (matches watch OFF WRIST)

---

## Samsung Health Sensor SDK (physical Watch4)

Logcat tag: **`EWPipeline`**

Known failures on API 36 Watch4:
- `PERMISSION_ERROR` on HR → need `android.permission.health.READ_HEART_RATE` + user grant
- `SDK_POLICY_ERROR` on accel → enable **Health Sensor Service Developer Mode** on the watch:
  1. Settings → Apps → Health Sensor Service (or Health Platform)
  2. Tap title ~10 times → Developer mode ON

Until Samsung works, app falls back to Android SensorManager (accel may work; HR often null off-wrist).

---

## Run / debug rules

1. Run **`:wear` on SM-R875F** for real telemetry  
2. Run **`:mobile` on phone** only for companion UI  
3. Filter Logcat: `EWPipeline`  
4. Success: `Room insert` + `telemetry HTTP 200 accepted=`  
5. Do not invent API JSON / endpoints  
6. Do not inject fake sensor values into telemetry  
7. Do not redesign `:wear` unless fixing the live pipeline

---

## Definition of “fixed” for physical watch

1. Watch on wrist → `contact=true` and live `hr` changes on watch UI  
2. Same patient name on watch Status and Lovable card  
3. Lovable HR/SpO2/contact match **latest telemetry**, not baselines when telemetry is null  
4. Physical device visually distinct from FAKE/DEMO users on Lovable  

Android repo owns (1–2). Lovable web owns (3–4).
