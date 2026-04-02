package com.ffxx68.obdreader;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // UUID standard per Serial Port Profile (SPP) - usato da ELM327
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int READ_INTERVAL_MS = 2000; // polling ogni 2 secondi
    private static final int MAX_LOG_LINES = 200;     // ~100 scambi comando/risposta
    private static final String PREFS_NAME = "OBDReaderPrefs";
    private static final String PREF_DEVICE_NAME = "selectedDeviceName";
    private static final String PREF_DEVICE_ADDRESS = "selectedDeviceAddress";
    private static final String PREF_PROTOCOL = "selectedProtocol";

    // Variabili statiche per condividere dati con SettingsActivity
    private static StringBuilder logBuffer = new StringBuilder();
    private static int selectedProtocol = R.id.rbAuto; // Default: Auto

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Stato connessione
    private boolean isConnected = false;
    private boolean isPolling = false;
    private boolean shouldStayConnected = false; // Flag per riconnessione automatica
    private int consecutiveBTErrors = 0;
    private int consecutiveNoData = 0; // Contatore per risposte "NO DATA" dall'ECU
    private static final int MAX_ERRORS_BEFORE_RECONNECT = 3;
    private static final int MAX_NO_DATA_BEFORE_RETRY = 5; // Dopo 5 "NO DATA" riprova init ECU

    // Handler per aggiornamenti UI dal thread BT
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    // UI Elements
    private TextView tvStatus;
    private TextView tvRpm;
    private TextView tvSpeed;
    private TextView tvTemp;
    private TextView tvFuelRateInstantMaf;
    private TextView tvFuelRateInstantSd;
    private TextView tvTotalKm;
    private TextView tvAvgSpeed;
    private TextView tvAvgFuelMaf;
    private TextView tvAvgFuelSd;
    private TextView tvProtocol;
    private TextView tvElmVersion;
    private TextView tvVin;
    private Button btnConnect;
    private Button btnDisconnect;
    private ProgressBar progressBar;
    private ImageButton btnSettings;

    // Statistiche di viaggio
    private double totalDistanceKm = 0.0;
    private double totalFuelMafLiters = 0.0;
    private double totalFuelSdLiters = 0.0;
    private long totalSpeedSum = 0;
    private int speedSampleCount = 0;
    private long lastUpdateTimeMs = 0;

    // Gestione log viaggi
    private TripLogManager tripLogManager;
    private TripLog currentTrip;

    // Lista dei PID supportati dalla modalità 01 (PID 01-20)
    private List<Integer> supportedPids01 = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tripLogManager = new TripLogManager(this);

        initViews();
        loadSavedProtocol();
        initBluetooth();
        setupListeners();
    }

    // ─── INIZIALIZZAZIONE ────────────────────────────────────────────────────

    private void loadSavedProtocol() {
        // Carica il protocollo salvato dalle SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedProtocol = prefs.getInt(PREF_PROTOCOL, R.id.rbAuto);
    }

    private void initViews() {
        tvStatus    = findViewById(R.id.tvStatus);
        tvRpm       = findViewById(R.id.tvRpm);
        tvSpeed     = findViewById(R.id.tvSpeed);
        tvTemp      = findViewById(R.id.tvTemp);
        tvFuelRateInstantMaf = findViewById(R.id.tvFuelRateInstantMaf);
        tvFuelRateInstantSd  = findViewById(R.id.tvFuelRateInstantSd);
        tvTotalKm   = findViewById(R.id.tvTotalKm);
        tvAvgSpeed  = findViewById(R.id.tvAvgSpeed);
        tvAvgFuelMaf = findViewById(R.id.tvAvgFuelMaf);
        tvAvgFuelSd  = findViewById(R.id.tvAvgFuelSd);
        tvProtocol  = findViewById(R.id.tvProtocol);
        tvElmVersion = findViewById(R.id.tvElmVersion);
        tvVin        = findViewById(R.id.tvVin);
        btnConnect      = findViewById(R.id.btnConnect);
        btnDisconnect   = findViewById(R.id.btnDisconnect);
        progressBar     = findViewById(R.id.progressBar);
        btnSettings     = findViewById(R.id.btnSettings);

        btnDisconnect.setEnabled(false);
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth non supportato su questo dispositivo");
            btnConnect.setEnabled(false);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            showStatus("Attiva il Bluetooth nelle impostazioni, poi riavvia l'app");
        } else {
            // Verifica se c'è un dispositivo salvato
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String deviceName = prefs.getString(PREF_DEVICE_NAME, null);
            if (deviceName != null) {
                showStatus("Pronto. Tocca 'Connetti' per collegarti a " + deviceName);
            } else {
                showStatus("Seleziona un dispositivo nelle Impostazioni");
                btnConnect.setEnabled(false);
            }
        }
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> connectToSavedDevice());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Verifica se il dispositivo è stato selezionato nelle impostazioni
        if (!isConnected && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String deviceName = prefs.getString(PREF_DEVICE_NAME, null);
            if (deviceName != null) {
                showStatus("Pronto. Tocca 'Connetti' per collegarti a " + deviceName);
                btnConnect.setEnabled(true);
            } else {
                showStatus("Seleziona un dispositivo nelle Impostazioni");
                btnConnect.setEnabled(false);
            }
        }
    }

    // ─── CONNESSIONE AL DISPOSITIVO SALVATO ─────────────────────────────────

    private void connectToSavedDevice() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceAddress = prefs.getString(PREF_DEVICE_ADDRESS, null);
        String deviceName = prefs.getString(PREF_DEVICE_NAME, null);

        if (deviceAddress == null) {
            showStatus("Nessun dispositivo selezionato. Vai nelle Impostazioni.");
            return;
        }

        // Imposta il flag per riconnessione automatica
        shouldStayConnected = true;
        consecutiveBTErrors = 0;
        consecutiveNoData = 0;

        // Verifica permessi
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            return;
        }

        // Ottieni il dispositivo e connetti
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device);
        } catch (IllegalArgumentException e) {
            showStatus("Indirizzo dispositivo non valido: " + deviceAddress);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                connectToSavedDevice();
            } else {
                showStatus("Permessi Bluetooth negati. L'app non può funzionare.");
            }
        }
    }

    // ─── CONNESSIONE BLUETOOTH ───────────────────────────────────────────────

    private void connectToDevice(BluetoothDevice device) {
        showStatus("Connessione a " + device.getName() + "...");
        showProgress(true);
        btnConnect.setEnabled(false);

        new Thread(() -> {
            try {
                // Chiudi eventuale connessione precedente
                closeStreams();

                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();

                inputStream  = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();

                // Inizializzazione ELM327
                initElm327();

                isConnected = true;
                consecutiveBTErrors = 0; // Reset contatore errori
                consecutiveNoData = 0; // Reset contatore NO DATA
                mainHandler.post(() -> {
                    showStatus("Connesso alla ECU");
                    showProgress(false);
                    btnConnect.setEnabled(true);
                    btnDisconnect.setEnabled(true);
                    // Avvia automaticamente l'aggiornamento continuo
                    startPolling();
                    // Crea il record del viaggio alla connessione SOLO se non esiste già (prima connessione)
                    if (currentTrip == null) {
                        currentTrip = new TripLog();
                        tripLogManager.saveTrip(currentTrip);
                    }
                });

            } catch (EcuConnectionException e) {
                // BT connesso ma la ECU non risponde
                isConnected = true; // rimane connesso al BT
                mainHandler.post(() -> {
                    showStatus("Connesso a: " + device.getName()
                            + "\nErrore comunicazione ECU: " + e.getMessage()
                            + "\nVerifica protocollo o chiave in posizione ON.");
                    showProgress(false);
                    btnConnect.setEnabled(true);
                    btnDisconnect.setEnabled(true);
                });

            } catch (IOException e) {
                isConnected = false;
                mainHandler.post(() -> {
                    showStatus("Errore connessione BT: " + e.getMessage()
                            + "\nAssicurati che l'ELM327 sia acceso e nel raggio BT.");
                    showProgress(false);
                    btnConnect.setEnabled(true);
                });
            }
        }).start();
    }

    // ─── INIZIALIZZAZIONE ELM327 ─────────────────────────────────────────────

    /**
     * Sequenza di inizializzazione standard per chip ELM327.
     * Tutti i comandi "AT" sono specifici del firmware ELM327
     * e non fanno parte del protocollo OBD-II standard.
     */
    private void initElm327() throws IOException {
        String elmVersion = sendCommand("ATZ", 1500);  // Soft reset — risposta: "ELM327 v1.x"
        sendCommand("ATE0", 500);    // Echo OFF
        sendCommand("ATL0", 300);    // Linefeeds OFF
        sendCommand("ATS0", 300);    // Spaces OFF
        sendCommand("ATH0", 300);    // Headers OFF
        sendCommand("ATAT1", 300);   // Adaptive timing ON

        // Determina protocollo in base alla selezione UI
        String protoCmd;
        String protoLabel;

        if (selectedProtocol == R.id.rbSP3) {
            protoCmd   = "ATSP3";
            protoLabel = "ISO 9141-2 (SP3)";
        } else if (selectedProtocol == R.id.rbSP5) {
            protoCmd   = "ATSP5";
            protoLabel = "KWP Fast Init (SP5)";
        } else {
            protoCmd   = "ATSP0";
            protoLabel = "Auto-detect (SP0)";
        }

        sendCommand(protoCmd, 1000);

        // Verifica comunicazione con la ECU tramite Mode 01 PID 00 (PID supportati)
        String ecuTestRaw = sendCommand("0100", 3000);
        boolean containsBusInit = ecuTestRaw.toUpperCase().contains("BUS INIT");
        boolean containsOk = ecuTestRaw.toUpperCase().contains("OK");
        boolean ecuOk = !ecuTestRaw.isEmpty()
                && !ecuTestRaw.toUpperCase().contains("NO DATA")
                && !ecuTestRaw.toUpperCase().contains("UNABLE TO CONNECT")
                && !ecuTestRaw.toUpperCase().contains("ERROR")
                && !ecuTestRaw.contains("?")
                //&& ecuTestRaw.toUpperCase().contains("41")
                && !(containsBusInit && !containsOk); // Se c'è BUS INIT ma non OK, fallisce; se c'è anche OK, è valido

        // Estrazione e salvataggio PID supportati (solo se risposta valida)
        supportedPids01.clear();
        if (ecuOk) {
            // Cerca la parte dopo OK (se presente)
            String raw = ecuTestRaw.toUpperCase();
            int okIdx = raw.indexOf("OK");
            if (okIdx >= 0) {
                raw = raw.substring(okIdx + 2); // dopo "OK"
            }
            // Cerca header 4100
            int idx = raw.indexOf("4100");
            if (idx >= 0 && raw.length() >= idx + 12) {
                String payload = raw.substring(idx + 4, idx + 12); // 8 caratteri esadecimali (4 byte)
                // Converte in 4 byte
                try {
                    int[] bytes = new int[4];
                    for (int i = 0; i < 4; i++) {
                        bytes[i] = Integer.parseInt(payload.substring(i * 2, i * 2 + 2), 16);
                    }
                    // Ogni bit rappresenta un PID supportato (da 01 a 20)
                    for (int i = 0; i < 32; i++) {
                        int byteIdx = i / 8;
                        int bitIdx = 7 - (i % 8); // bit più significativo a sinistra
                        if (((bytes[byteIdx] >> bitIdx) & 0x01) == 1) {
                            supportedPids01.add(i + 1); // PID da 0x01 a 0x20
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // Leggi VIN (Mode 09 PID 02) — non sempre supportato da tutti i veicoli
        String rawVin = ecuOk ? sendCommand("0902", 1500) : "";
        String vin = parseVin(rawVin);

        final String label   = protoLabel;
        final String elmVer  = elmVersion.isEmpty() ? "?" : elmVersion;
        final String vinText = vin;
        mainHandler.post(() -> {
            tvProtocol.setText("Protocollo: " + label);
            tvElmVersion.setText("ELM327: " + elmVer);
            tvVin.setText("VIN: " + vinText);
        });

        if (!ecuOk) {
            throw new EcuConnectionException(ecuTestRaw.trim().isEmpty() ? "nessuna risposta" : ecuTestRaw.trim());
        }
    }

    /** Eccezione specifica per errori di comunicazione con la ECU (distinta da errori BT). */
    static class EcuConnectionException extends IOException {
        EcuConnectionException(String message) { super(message); }
    }

    /**
     * Estrae il VIN ASCII dalla risposta grezza di 0902.
     * Formato risposta ELM327 (headers off, spaces off):
     *   490201 + 11 byte hex ASCII del VIN
     * Alcuni veicoli rispondono su più righe: "4902 01 XX XX XX..."
     */
    private String parseVin(String raw) {
        if (raw == null || raw.isEmpty() || raw.contains("NO DATA")
                || raw.contains("ERROR") || raw.contains("?")) {
            return "N/D";
        }
        // Rimuove spazi e cerca il marker "490201" o "49 02 01"
        String hex = raw.replaceAll("\\s+", "").toUpperCase();
        int idx = hex.indexOf("490201");
        if (idx < 0) idx = hex.indexOf("4902");
        if (idx < 0) return "N/D";
        // Salta i byte header (490201 = 6 char)
        String vinHex = hex.substring(idx + 6);
        // Converti i byte hex in caratteri ASCII
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < vinHex.length() && sb.length() < 17; i += 2) {
            try {
                int b = Integer.parseInt(vinHex.substring(i, i + 2), 16);
                if (b >= 0x20 && b <= 0x7E) sb.append((char) b);
            } catch (NumberFormatException ignored) {}
        }
        return sb.length() > 0 ? sb.toString() : "N/D";
    }

    // ─── COMANDI OBD-II ──────────────────────────────────────────────────────

    /**
     * Invia un comando AT o OBD-II all'ELM327 e legge la risposta.
     * Il terminatore '\r' è richiesto dall'ELM327.
     * Il chip risponde con '>' (prompt) quando è pronto per il prossimo comando.
     */
    private String sendCommand(String command, int delayMs) throws IOException {
        outputStream.write((command + "\r").getBytes());
        outputStream.flush();
        try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); };

        StringBuilder response = new StringBuilder();
        long timeout = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < timeout) {
            if (inputStream.available() > 0) {
                int b = inputStream.read();
                char c = (char) b;
                if (c == '>') break; // ELM327 pronto per il prossimo comando
                response.append(c);
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); };
            }
        }
        String result = response.toString().replace("\r", "").replace("\n", " ").trim();

        final String logLine = ">> " + command + "\n<< " + result + "\n";
        mainHandler.post(() -> {
            // Aggiungi al buffer statico
            logBuffer.append(logLine);

            // Mantieni il buffer entro MAX_LOG_LINES righe
            String current = logBuffer.toString();
            String[] lines = current.split("\n", -1);
            if (lines.length > MAX_LOG_LINES) {
                int excess = lines.length - MAX_LOG_LINES;
                StringBuilder trimmed = new StringBuilder();
                for (int i = excess; i < lines.length; i++) {
                    trimmed.append(lines[i]).append("\n");
                }
                logBuffer = trimmed;
            }
        });

        return result;
    }

    // Overload senza sleep custom
    private String sendCommand(String command) throws IOException {
        return sendCommand(command, 300);
    }

    // ─── LETTURA DATI ────────────────────────────────────────────────────────

    private void startPolling() {
        if (isPolling) return; // Già in polling

        isPolling = true;

        // Reset statistiche di viaggio SOLO se è una nuova connessione (non una riconnessione)
        if (currentTrip == null || totalDistanceKm == 0) {
            totalDistanceKm = 0.0;
            totalFuelMafLiters = 0.0;
            totalFuelSdLiters = 0.0;
            totalSpeedSum = 0;
            speedSampleCount = 0;
        }
        lastUpdateTimeMs = System.currentTimeMillis();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;

                new Thread(() -> {
                    try {
                        OBDData data = fetchOBDData();
                        consecutiveBTErrors = 0; // Reset errori se la lettura ha successo
                        consecutiveNoData = 0; // Reset contatore NO DATA
                        mainHandler.post(() -> {
                            updateUI(data);
                            if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                        });

                    } catch (EcuNoDataException e) {
                        // ECU non fornisce dati (motore probabilmente spento)
                        consecutiveNoData++;
                        consecutiveBTErrors = 0; // Non è un errore di connessione BT

                        mainHandler.post(() -> {
                            if (consecutiveNoData >= MAX_NO_DATA_BEFORE_RETRY && shouldStayConnected) {
                                // Tenta di re-inizializzare l'ECU
                                showStatus("ECU non risponde. Attendo riavvio motore... (" + consecutiveNoData + ")");

                                // Prova a re-inizializzare l'ELM327/ECU
                                new Thread(() -> {
                                    try {
                                        initElm327();
                                        consecutiveNoData = 0;
                                        mainHandler.post(() -> {
                                            showStatus("ECU rilevata! Ripresa lettura dati.");
                                        });
                                    } catch (Exception initError) {
                                        // Re-init fallita, continua ad aspettare
                                        mainHandler.post(() -> {
                                            showStatus("Motore spento. In attesa... (" + consecutiveNoData + ")");
                                        });
                                    }
                                    if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                                }).start();
                            } else {
                                showStatus("Motore spento? Tentativo " + consecutiveNoData + "/" + MAX_NO_DATA_BEFORE_RETRY);
                                if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                            }
                        });

                    } catch (IOException e) {
                        // Errore di connessione Bluetooth
                        consecutiveBTErrors++;
                        mainHandler.post(() -> {
                            if (consecutiveBTErrors >= MAX_ERRORS_BEFORE_RECONNECT && shouldStayConnected) {
                                // Tentativo di riconnessione automatica (BT perso)
                                showStatus("Connessione BT persa. Riconnessione...");
                                isConnected = false;
                                closeStreams();

                                // Riprova a connettersi dopo 2 secondi
                                mainHandler.postDelayed(() -> {
                                    if (shouldStayConnected) {
                                        connectToSavedDevice();
                                    }
                                }, 2000);
                            } else if (consecutiveBTErrors < MAX_ERRORS_BEFORE_RECONNECT) {
                                // Mostra errore ma continua a provare
                                showStatus("Errore lettura (" + consecutiveBTErrors + "/" + MAX_ERRORS_BEFORE_RECONNECT + "): " + e.getMessage());
                                if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                            } else {
                                // Troppi errori e non dobbiamo riconnettere
                                showStatus("Connessione persa: " + e.getMessage());
                                stopPolling();
                            }
                        });
                    }
                }).start();
            }
        };
        mainHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingRunnable != null) mainHandler.removeCallbacks(pollingRunnable);
    }

    /**
     * Recupera tutti i dati OBD-II dal veicolo.
     * I PID (Parameter IDs) sono definiti dallo standard SAE J1979.
     *
     * Formato risposta: "41 XX AA BB" dove:
     *   41 = risposta al mode 01
     *   XX = PID richiesto
     *   AA, BB = byte dati
     */
    private OBDData fetchOBDData() throws IOException {
        OBDData data = new OBDData();

        // RPM — PID 0x0C — Formula: ((A*256)+B)/4 → giri/min
        String rpmRaw = sendCommand("010C");
        data.rpm = parseRpm(rpmRaw);

        // Controlla se l'ECU sta rispondendo con dati validi
        if (rpmRaw.toUpperCase().contains("NO DATA") ||
            rpmRaw.toUpperCase().contains("UNABLE TO CONNECT") ||
            rpmRaw.toUpperCase().contains("STOPPED") ||
            data.rpm < 0) {
            throw new EcuNoDataException("ECU non fornisce dati (motore spento?)");
        }

        // Velocità — PID 0x0D — Formula: A → km/h (valore diretto)
        String speedRaw = sendCommand("010D");
        data.speedKmh = parseSpeed(speedRaw);

        // Temperatura liquido raffreddamento — PID 0x05 — Formula: A-40 → °C
        String tempRaw = sendCommand("0105");
        data.tempCelsius = parseTemp(tempRaw);

        // MAF — PID 0x10 — Formula: ((A*256)+B)/100 → g/s
        String mafRaw = sendCommand("0110");
        data.mafGps = parseMaf(mafRaw);

        // MAP — PID 0x0B — Formula: A → kPa (pressione collettore, usata per Speed-Density)
        String mapRaw = sendCommand("010B");
        data.mapKpa = parseMap(mapRaw);

        // IAT — PID 0x0F — Formula: A-40 → °C (temperatura aria aspirata)
        String iatRaw = sendCommand("010F");
        data.iatCelsius = parseIat(iatRaw);

        return data;
    }

    /** Eccezione per quando l'ECU non fornisce dati (es. motore spento) */
    static class EcuNoDataException extends IOException {
        EcuNoDataException(String message) { super(message); }
    }

    // ─── PARSER RISPOSTE OBD-II ──────────────────────────────────────────────

    /**
     * Estrae i byte hex dalla risposta ELM327.
     * Esempio risposta: "410C1A2B" → header="410C" → bytes=[0x1A, 0x2B]
     */
    private List<Integer> extractBytes(String raw, String header) {
        String clean = raw.replaceAll("\\s+", "").toUpperCase();
        int idx = clean.indexOf(header.toUpperCase());
        if (idx < 0) return Collections.emptyList();
        String hexData = clean.substring(idx + header.length());
        List<Integer> bytes = new ArrayList<>();
        for (int i = 0; i + 1 < hexData.length(); i += 2) {
            try {
                bytes.add(Integer.parseInt(hexData.substring(i, i + 2), 16));
            } catch (NumberFormatException e) {
                break;
            }
        }
        return bytes;
    }

    private int parseRpm(String raw) {
        List<Integer> b = extractBytes(raw, "410C");
        if (b.size() < 2) return -1;
        return ((b.get(0) * 256) + b.get(1)) / 4;
    }

    private int parseSpeed(String raw) {
        List<Integer> b = extractBytes(raw, "410D");
        return b.isEmpty() ? -1 : b.get(0);
    }

    private int parseTemp(String raw) {
        List<Integer> b = extractBytes(raw, "4105");
        return b.isEmpty() ? -1 : b.get(0) - 40;
    }

    private float parseMaf(String raw) {
        List<Integer> b = extractBytes(raw, "4110");
        if (b.size() < 2) return -1f;
        return ((b.get(0) * 256) + b.get(1)) / 100.0f;
    }

    private int parseMap(String raw) {
        List<Integer> b = extractBytes(raw, "410B");
        return b.isEmpty() ? -1 : b.get(0); // kPa
    }

    private int parseIat(String raw) {
        List<Integer> b = extractBytes(raw, "410F");
        return b.isEmpty() ? -100 : b.get(0) - 40; // °C, -100 = non disponibile
    }

    /**
     * Calcola il consumo istantaneo in L/100km dal MAF e dalla velocità.
     * Formula: L/100km = (MAF / AFR / densità_gasolio) / (speed / 3600) * 100
     *   AFR gasolio stechiometrico = 14.5
     *   densità gasolio ≈ 0.84 kg/L
     */
    private float calcFuelRateMaf(float mafGps, int speedKmh) {
        if (mafGps < 0 || speedKmh <= 0) return -1f;
        float litersPerSec = mafGps / 14.5f / 0.84f;
        float speedMs = speedKmh / 3.6f;
        return (litersPerSec / speedMs) * 100f;
    }

    /**
     * Calcola il consumo istantaneo con metodo Speed-Density (MAP + cilindrata + VE).
     * Formula portata aria:
     *   airflow (g/s) = (displacement_L * RPM/2 * VE * MAP_kPa * M_air) / (R * T_K * 60)
     *   dove M_air=28.97 g/mol, R=8.314 J/(mol·K)
     *   T_K = temperatura aria aspirata (IAT) in Kelvin — letta dalla ECU, fallback 25°C
     *
     * Parametri fissi per diesel turbo 1.9 TDI tipico:
     *   cilindrata = 1.9 L, VE = 0.92
     */
    private float calcFuelRateSpeedDensity(int rpm, int speedKmh, int mapKpa, int iatCelsius) {
        if (rpm <= 0 || speedKmh <= 0 || mapKpa <= 0) return -1f;
        final float displacement = 1.9f;  // litri
        final float ve           = 0.92f; // efficienza volumetrica turbo diesel
        final float mAir         = 28.97f;
        final float R            = 8.314f;
        // Usa IAT reale dalla ECU se disponibile, altrimenti 25°C
        final float tKelvin = (iatCelsius > -100) ? (iatCelsius + 273.15f) : 298f;
        // Portata aria stimata in g/s
        float airflowGs = (displacement * (rpm / 2f) * ve * mapKpa * 1000f * mAir)
                        / (R * tKelvin * 60f * 1000f);
        float litersPerSec = airflowGs / 14.5f / 0.84f;
        float speedMs = speedKmh / 3.6f;
        return (litersPerSec / speedMs) * 100f;
    }

    // ─── UI HELPERS ──────────────────────────────────────────────────────────

    private void updateUI(OBDData data) {
        // RPM
        tvRpm.setText(data.rpm >= 0
                ? "RPM: " + data.rpm
                : "RPM: N/D");

        // Velocità
        tvSpeed.setText(data.speedKmh >= 0
                ? "Velocita: " + data.speedKmh + " km/h"
                : "Velocita: N/D");

        // Temperatura
        tvTemp.setText(data.tempCelsius > -40
                ? "Temp: " + data.tempCelsius + " C"
                : "Temp: N/D");

        // Calcola consumi in L/100km
        float fuelMafL100 = calcFuelRateMaf(data.mafGps, data.speedKmh);
        float fuelSdL100  = calcFuelRateSpeedDensity(data.rpm, data.speedKmh, data.mapKpa, data.iatCelsius);

        // Converte L/100km in km/L istantanei
        float kmLMaf = fuelMafL100 > 0 ? 100.0f / fuelMafL100 : -1f;
        float kmLSd = fuelSdL100 > 0 ? 100.0f / fuelSdL100 : -1f;

        // Visualizza km/L istantanei
        tvFuelRateInstantMaf.setText(kmLMaf > 0
                ? String.format(java.util.Locale.US, "km/L ist. (MAF): %.2f", kmLMaf)
                : (data.speedKmh == 0 ? "km/L ist. (MAF): fermo" : "km/L ist. (MAF): N/D"));

        tvFuelRateInstantSd.setText(kmLSd > 0
                ? String.format(java.util.Locale.US, "km/L ist. (SD): %.2f", kmLSd)
                : (data.speedKmh == 0 ? "km/L ist. (SD): fermo" : "km/L ist. (SD): N/D"));

        // Calcola statistiche di viaggio
        long currentTimeMs = System.currentTimeMillis();
        if (lastUpdateTimeMs > 0 && data.speedKmh > 0) {
            double elapsedHours = (currentTimeMs - lastUpdateTimeMs) / 3600000.0; // ms to hours
            double distanceKm = data.speedKmh * elapsedHours;
            totalDistanceKm += distanceKm;

            // Accumula consumo carburante
            if (fuelMafL100 > 0) {
                totalFuelMafLiters += (distanceKm / 100.0) * fuelMafL100;
            }
            if (fuelSdL100 > 0) {
                totalFuelSdLiters += (distanceKm / 100.0) * fuelSdL100;
            }

            // Accumula velocità per media
            totalSpeedSum += data.speedKmh;
            speedSampleCount++;
        }
        lastUpdateTimeMs = currentTimeMs;

        // Visualizza km percorsi
        tvTotalKm.setText(String.format(java.util.Locale.US, "Km percorsi: %.2f", totalDistanceKm));

        // Velocità media
        if (speedSampleCount > 0) {
            double avgSpeed = (double) totalSpeedSum / speedSampleCount;
            tvAvgSpeed.setText(String.format(java.util.Locale.US, "Velocita media: %.1f km/h", avgSpeed));
        } else {
            tvAvgSpeed.setText("Velocita media: --");
        }

        // km/L medi
        if (totalDistanceKm > 0.01) {
            if (totalFuelMafLiters > 0) {
                double avgKmLMaf = totalDistanceKm / totalFuelMafLiters;
                tvAvgFuelMaf.setText(String.format(java.util.Locale.US, "km/L medio (MAF): %.2f", avgKmLMaf));
            } else {
                tvAvgFuelMaf.setText("km/L medio (MAF): --");
            }

            if (totalFuelSdLiters > 0) {
                double avgKmLSd = totalDistanceKm / totalFuelSdLiters;
                tvAvgFuelSd.setText(String.format(java.util.Locale.US, "km/L medio (SD): %.2f", avgKmLSd));
            } else {
                tvAvgFuelSd.setText("km/L medio (SD): --");
            }
        } else {
            tvAvgFuelMaf.setText("km/L medio (MAF): --");
            tvAvgFuelSd.setText("km/L medio (SD): --");
        }

        // Aggiorna il record del viaggio corrente ad ogni lettura
        if (currentTrip != null && totalDistanceKm > 0) {
            double avgSpeed = speedSampleCount > 0 ? (double) totalSpeedSum / speedSampleCount : 0.0;
            double avgKmLMaf = totalFuelMafLiters > 0 ? totalDistanceKm / totalFuelMafLiters : 0.0;
            double avgKmLSd = totalFuelSdLiters > 0 ? totalDistanceKm / totalFuelSdLiters : 0.0;

            currentTrip.updateTrip(totalDistanceKm, avgSpeed, avgKmLMaf, avgKmLSd);
            tripLogManager.updateCurrentTrip(currentTrip);
        }
    }

    private void showStatus(String msg) {
        tvStatus.setText(msg);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ─── DISCONNESSIONE ──────────────────────────────────────────────────────

    private void disconnect() {
        shouldStayConnected = false; // Disabilita riconnessione automatica
        stopPolling();

        // Chiude il record del viaggio alla disconnessione
        if (currentTrip != null && totalDistanceKm > 0.01) {
            double avgSpeed = speedSampleCount > 0 ? (double) totalSpeedSum / speedSampleCount : 0.0;
            double avgKmLMaf = totalFuelMafLiters > 0 ? totalDistanceKm / totalFuelMafLiters : 0.0;
            double avgKmLSd = totalFuelSdLiters > 0 ? totalDistanceKm / totalFuelSdLiters : 0.0;

            currentTrip.endTrip(totalDistanceKm, avgSpeed, avgKmLMaf, avgKmLSd);
            tripLogManager.updateCurrentTrip(currentTrip);
            currentTrip = null;
        }

        isConnected = false;
        consecutiveBTErrors = 0;
        consecutiveNoData = 0;
        closeStreams();

        showStatus("Disconnesso.");
        btnDisconnect.setEnabled(false);
        tvRpm.setText("RPM: --");
        tvSpeed.setText("Velocita: --");
        tvTemp.setText("Temp: --");
        tvFuelRateInstantMaf.setText("km/L ist. (MAF): --");
        tvFuelRateInstantSd.setText("km/L ist. (SD): --");
        tvTotalKm.setText("Km percorsi: --");
        tvAvgSpeed.setText("Velocita media: --");
        tvAvgFuelMaf.setText("km/L medio (MAF): --");
        tvAvgFuelSd.setText("km/L medio (SD): --");
        tvProtocol.setText("Protocollo: --");
        tvElmVersion.setText("ELM327: --");
        tvVin.setText("VIN: --");
    }

    private void closeStreams() {
        try { if (inputStream  != null) inputStream.close();  } catch (IOException ignored) {}
        try { if (outputStream != null) outputStream.close(); } catch (IOException ignored) {}
        try { if (bluetoothSocket != null) bluetoothSocket.close(); } catch (IOException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    // ─── MODELLO DATI ────────────────────────────────────────────────────────

    static class OBDData {
        int rpm = -1;
        int speedKmh = -1;
        int tempCelsius = -41; // -41 = non valido (minimo valido è -40°C)
        float mafGps = -1f;   // g/s, -1 = non disponibile
        int mapKpa = -1;      // kPa, -1 = non disponibile
        int iatCelsius = -100; // °C, -100 = non disponibile
    }

    // ─── METODI STATICI PER CONDIVISIONE DATI CON SETTINGSACTIVITY ──────────

    public static String getLog() {
        return logBuffer.toString();
    }

    public static int getSelectedProtocol() {
        return selectedProtocol;
    }

    public static void setSelectedProtocol(int protocol) {
        selectedProtocol = protocol;
    }
}
