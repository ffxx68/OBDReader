# OBD-II Trip Log — Android (Java)

Android app to read vehicle data via a Bluetooth ELM327 adapter.

---

## Key features

- Bluetooth connection to ELM327 adapters (auto-detect protocol)
- Real-time data: engine RPM, vehicle speed, distance, average speed, fuel consumption (instant and average)
- Logging of fuel consumption and distance for analysis and reports
- Support for common ELM327 commands for reliable communication

---

## Requirements

- Bluetooth ELM327 adapter (recommended v2.3+)
- Android 6.0+ (API 23+)
- Vehicle with OBD-II port

---

## Quick start

1. Insert the ELM327 adapter into the vehicle OBD-II port
2. Pair the device via Bluetooth (common PINs: `1234`, `0000`, `6789`)
3. Start the engine, open the app → Settings → Scan devices → Connect

---


## Notes

- Release build available at `app/release/app-release.aab`
