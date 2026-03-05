# OBD-II Reader — App Android (Java)

App nativa Android per leggere dati dal veicolo tramite adattatore Bluetooth ELM327.

---

## Funzionalità

| Funzione | Dettaglio |
|---|---|
| **RPM motore** | PID 010C — Formula: (A×256+B)/4 |
| **Velocità** | PID 010D — Formula: A km/h |
| **Temperatura** | PID 0105 — Formula: A−40 °C |
| **Codici errore DTC** | Mode 03 — Decodifica P/C/B/U |
| **Cancella DTC** | Mode 04 — Resetta la spia motore |
| **Polling continuo** | Aggiornamento ogni 2 secondi |

---

## Come aprire su Replit (online, senza installare nulla)

1. Vai su [replit.com](https://replit.com) e crea un account gratuito
2. Clicca **"Create Repl"** → cerca template **"Android (Gradle)"**
3. Carica questa cartella (o copia i file nelle posizioni indicate)
4. Clicca **Run** → Replit compila e genera l'APK
5. Scarica l'APK e installalo sul tuo telefono

---

## Come aprire su Gitpod (alternativa)

1. Metti il progetto su GitHub
2. Vai su `gitpod.io/#https://github.com/tuoutente/OBDReader`
3. Nel terminale: `./gradlew assembleDebug`
4. Scarica `app/build/outputs/apk/debug/app-debug.apk`

---

## Struttura del progetto

```
OBDReader/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/obdreader/
│   │   │   └── MainActivity.java       ← tutta la logica BT + OBD
│   │   └── res/
│   │       ├── layout/activity_main.xml ← interfaccia grafica
│   │       ├── values/styles.xml
│   │       └── drawable/ic_launcher.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
└── gradle.properties
```

---

## Prerequisiti hardware

- Adattatore **ELM327 v1.5+** Bluetooth (Classic BT, non BLE)
- Android 6.0+ (API 23)
- Veicolo con porta OBD-II (tutti i veicoli EU dal 2001, US dal 1996)

## Prima dell'uso

1. Inserisci l'adattatore ELM327 nella porta OBD-II del veicolo
2. Vai in **Impostazioni → Bluetooth** sul tuo Android
3. Accoppia il dispositivo ELM327 (PIN: `1234` o `6789` o `0000`)
4. Apri l'app → **Cerca dispositivi** → seleziona l'ELM327

---

## Comandi ELM327 utilizzati

| Comando | Scopo |
|---|---|
| `ATZ` | Reset chip |
| `ATE0` | Echo OFF |
| `ATL0` | Linefeed OFF |
| `ATS0` | Spazi OFF |
| `ATH0` | Headers OFF |
| `ATAT1` | Adaptive timing |
| `ATSP0` | Auto-detect protocollo |
| `010C` | Leggi RPM |
| `010D` | Leggi velocità |
| `0105` | Leggi temperatura |
| `03` | Leggi DTC |
| `04` | Cancella DTC |
