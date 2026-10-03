# Emergency Watch — Android Studio run guide

**Open only:** `Desktop\EMERGENCY WATCH 2`  
Also read: `AGENTS.md` and `LOVABLE_VS_WATCH.md`

---

## Run the right app on the right device

| What you want | Run config | Device |
|---|---|---|
| Real sensors → Lovable backend | **`wear`** | **Samsung SM-R875F** |
| Phone companion UI | **`mobile`** | Phone / Phone_API36 emulator |

Never run `mobile` on the watch. Never run `wear` on a phone emulator.

---

## Watch vs Lovable (why they disagree)

| | Physical Watch UI | Lovable card (often) |
|---|---|---|
| Label | **PHYSICAL WATCH · DEV** | Looks like a normal ward patient |
| HR | Live BPM or `--` | Baseline ~70/73 even with no live HR |
| SpO2 | `-- (not measured)` | Often **97% demo/baseline** |
| Contact | ON WRIST / OFF WRIST | Detached / Active mix |

**If SpO2 is 97% on Lovable but `--` on the watch, Lovable is showing FAKE/BASELINE, not the watch.**

Website FAKE vs PHYSICAL badges are a **Lovable web** change (see `LOVABLE_VS_WATCH.md`).

---

## ONE-TIME on Watch4

1. Put watch **on wrist**
2. Enable Samsung **Health Sensor Service → Developer mode** (tap title ~10 times)
3. Allow Sensors / Heart rate / Background / Notifications
4. Run `:wear` → open **Status** on watch and compare to Lovable for the **same patient name**

---

## Logcat filter

`EWPipeline` on device **SM-R875F**

Good: `Room insert` + `telemetry HTTP 200 accepted=`
