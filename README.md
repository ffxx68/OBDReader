# OBD-II Reader — Android App (Java)

Native Android app to read vehicle data via Bluetooth ELM327 adapter.

---

## Features

- Bluetooth connectivity to ELM327 OBD-II devices
- Real-time data display:
  - Engine RPM
  - Vehicle speed
  - Coolant temperature
  - Distance traveled
  - Average speed
  - Fuel consumption, instantaneous and average
- Fuel consumption and distance data logging

---

## Hardware Requirements

- **ELM327 v2.3+** Bluetooth adapter
- Android 6.0+ (API 23)
- Vehicle with OBD-II port (all EU vehicles from 2001, US from 1996)

## Before Using

1. Insert the ELM327 adapter into the vehicle's OBD-II port
2. Go to **Settings → Bluetooth** on your Android device
3. Pair the ELM327 device (PIN: `1234` or `6789` or `0000`)
4. Start vehicle ignition (engine running)
5. Open the app → Go to **Settings** page → **Search devices** → select your ELM327 device from the list → **Connect**

---

## Main ELM327 Commands Used

| Command | Purpose |
|---|---|
| `ATZ` | Reset chip |
| `ATE0` | Echo OFF |
| `ATL0` | Linefeed OFF |
| `ATS0` | Spaces OFF |
| `ATH0` | Headers OFF |
| `ATAT1` | Adaptive timing |
| `ATSP0` | Auto-detect protocol |
| `010C` | Read engine RPM |
| `010D` | Read speed |
| `0105` | Read coolant temperature |
| `0110` | Read MAF (mass air flow) |
| `010B` | Read MAP (manifold pressure) |
| `010F` | Read IAT (intake air temperature) |

