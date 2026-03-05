package com.example.obdreader;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Stato connessione
    private boolean isConnected = false;
    private boolean isPolling = false;

    // Handler per aggiornamenti UI dal thread BT
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    // UI Elements
    private TextView tvStatus;
    private TextView tvRpm;
    private TextView tvSpeed;
    private TextView tvTemp;
    private TextView tvDtc;
    private TextView tvProtocol;
    private Button btnScan;
    private Button btnDisconnect;
    private Button btnReadOnce;
    private Button btnStartPolling;
    private Button btnClearDtc;
    private ProgressBar progressBar;

    // Lista dispositivi accoppiati
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBluetooth();
        setupListeners();
    }

    // ─── INIZIALIZZAZIONE ────────────────────────────────────────────────────

    private void initViews() {
        tvStatus    = findViewById(R.id.tvStatus);
        tvRpm       = findViewById(R.id.tvRpm);
        tvSpeed     = findViewById(R.id.tvSpeed);
        tvTemp      = findViewById(R.id.tvTemp);
        tvDtc       = findViewById(R.id.tvDtc);
        tvProtocol  = findViewById(R.id.tvProtocol);
        btnScan         = findViewById(R.id.btnScan);
        btnDisconnect   = findViewById(R.id.btnDisconnect);
        btnReadOnce     = findViewById(R.id.btnReadOnce);
        btnStartPolling = findViewById(R.id.btnStartPolling);
        btnClearDtc     = findViewById(R.id.btnClearDtc);
        progressBar     = findViewById(R.id.progressBar);

        setDataButtonsEnabled(false);
        btnDisconnect.setEnabled(false);
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showStatus("❌ Bluetooth non supportato su questo dispositivo");
            btnScan.setEnabled(false);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            showStatus("⚠️ Attiva il Bluetooth nelle impostazioni, poi riavvia l'app");
        } else {
            showStatus("✅ Bluetooth attivo. Tocca 'Cerca dispositivi' per iniziare.");
        }
    }

    private void setupListeners() {
        btnScan.setOnClickListener(v -> checkPermissionsAndScan());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnReadOnce.setOnClickListener(v -> readAllDataOnce());
        btnStartPolling.setOnClickListener(v -> togglePolling());
        btnClearDtc.setOnClickListener(v -> clearDtcCodes());
    }

    // ─── PERMESSI ────────────────────────────────────────────────────────────

    private void checkPermissionsAndScan() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        } else {
            // Android 6-11
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            scanAndShowDevices();
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
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            }
            if (allGranted) {
                scanAndShowDevices();
            } else {
                showStatus("❌ Permessi Bluetooth negati. L'app non può funzionare.");
            }
        }
    }

    // ─── SCANSIONE E SELEZIONE DISPOSITIVO ──────────────────────────────────

    private void scanAndShowDevices() {
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        pairedDevices.clear();
        List<String> names = new ArrayList<>();

        for (BluetoothDevice d : bonded) {
            pairedDevices.add(d);
            String name = d.getName() != null ? d.getName() : "Sconosciuto";
            names.add(name + "\n" + d.getAddress());
        }

        if (pairedDevices.isEmpty()) {
            showStatus("⚠️ Nessun dispositivo accoppiato.\n"
                    + "Vai in Impostazioni → Bluetooth e accoppia l'ELM327 (PIN: 1234 o 6789)");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Seleziona dispositivo ELM327")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    connectToDevice(pairedDevices.get(which));
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ─── CONNESSIONE BLUETOOTH ───────────────────────────────────────────────

    private void connectToDevice(BluetoothDevice device) {
        showStatus("🔄 Connessione a " + device.getName() + "...");
        showProgress(true);
        btnScan.setEnabled(false);

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
                mainHandler.post(() -> {
                    showStatus("✅ Connesso a: " + device.getName());
                    showProgress(false);
                    btnScan.setEnabled(true);
                    btnDisconnect.setEnabled(true);
                    setDataButtonsEnabled(true);
                });

            } catch (IOException e) {
                isConnected = false;
                mainHandler.post(() -> {
                    showStatus("❌ Errore connessione: " + e.getMessage()
                            + "\nAssicurati che l'ELM327 sia acceso e nel raggio BT.");
                    showProgress(false);
                    btnScan.setEnabled(true);
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
        sendCommand("ATZ", 1500);    // Soft reset — risposta: "ELM327 v1.x"
        sendCommand("ATE0", 500);    // Echo OFF — evita echo dei comandi nella risposta
        sendCommand("ATL0", 300);    // Linefeeds OFF — risposta più pulita
        sendCommand("ATS0", 300);    // Spaces OFF — rimuove spazi tra byte hex
        sendCommand("ATH0", 300);    // Headers OFF — nasconde header CAN/ISO
        sendCommand("ATAT1", 300);   // Adaptive timing ON — ottimizza timeout automaticamente
        String proto = sendCommand("ATSP0", 1000); // Auto-detect protocollo (ISO, CAN, KWP...)
        mainHandler.post(() -> tvProtocol.setText("Protocollo: " + proto.trim()));
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
        return response.toString().replace("\r", "").replace("\n", " ").trim();
    }

    // Overload senza sleep custom
    private String sendCommand(String command) throws IOException {
        return sendCommand(command, 300);
    }

    // ─── LETTURA DATI ────────────────────────────────────────────────────────

    private void readAllDataOnce() {
        if (!isConnected) return;
        showProgress(true);

        new Thread(() -> {
            try {
                OBDData data = fetchOBDData();
                mainHandler.post(() -> {
                    updateUI(data);
                    showProgress(false);
                });
            } catch (IOException e) {
                mainHandler.post(() -> {
                    showStatus("❌ Errore lettura: " + e.getMessage());
                    showProgress(false);
                });
            }
        }).start();
    }

    private void togglePolling() {
        if (!isPolling) {
            startPolling();
        } else {
            stopPolling();
        }
    }

    private void startPolling() {
        isPolling = true;
        btnStartPolling.setText("⏹ Stop aggiornamento continuo");
        btnReadOnce.setEnabled(false);

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling || !isConnected) return;
                new Thread(() -> {
                    try {
                        OBDData data = fetchOBDData();
                        mainHandler.post(() -> updateUI(data));
                    } catch (IOException e) {
                        mainHandler.post(() -> showStatus("⚠️ Errore polling: " + e.getMessage()));
                    }
                    if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                }).start();
            }
        };
        mainHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingRunnable != null) mainHandler.removeCallbacks(pollingRunnable);
        btnStartPolling.setText("🔁 Avvia aggiornamento continuo");
        btnReadOnce.setEnabled(true);
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

        // Velocità — PID 0x0D — Formula: A → km/h (valore diretto)
        String speedRaw = sendCommand("010D");
        data.speedKmh = parseSpeed(speedRaw);

        // Temperatura liquido raffreddamento — PID 0x05 — Formula: A-40 → °C
        String tempRaw = sendCommand("0105");
        data.tempCelsius = parseTemp(tempRaw);

        // Codici errore — Mode 03 (nessun PID)
        // Risposta: "43 P0300 P0301 ..." oppure "43 00" se nessun errore
        String dtcRaw = sendCommand("03", 1000);
        data.dtcCodes = parseDtc(dtcRaw);

        return data;
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

    /**
     * Decodifica i codici DTC dal formato ELM327.
     * Ogni DTC è codificato in 2 byte:
     *   Bit 7-6 del primo byte → tipo (P/C/B/U)
     *   Resto → codice numerico
     * Esempio: 43 01 03 → P0103
     */
    private String parseDtc(String raw) {
        if (raw == null || raw.isEmpty()
                || raw.contains("NO DATA")
                || raw.contains("NODATA")
                || raw.contains("OK")) {
            return "Nessun codice errore ✅";
        }

        String clean = raw.replaceAll("\\s+", "").toUpperCase();
        if (!clean.startsWith("43")) return "Risposta non valida: " + raw;

        String hexData = clean.substring(2);
        List<String> codes = new ArrayList<>();
        String[] types = {"P", "C", "B", "U"};

        for (int i = 0; i + 3 < hexData.length(); i += 4) {
            try {
                int firstNibble = Integer.parseInt(hexData.substring(i, i + 1), 16);
                int typeIdx = (firstNibble >> 2) & 0x03;
                int num1 = firstNibble & 0x03;
                String rest = hexData.substring(i + 1, i + 4);
                String code = types[typeIdx] + num1 + rest;
                if (!code.equals("P0000")) codes.add(code);
            } catch (NumberFormatException ignored) {}
        }

        return codes.isEmpty() ? "Nessun codice errore ✅" : String.join(", ", codes);
    }

    // ─── CANCELLAZIONE DTC ───────────────────────────────────────────────────

    private void clearDtcCodes() {
        if (!isConnected) return;
        new AlertDialog.Builder(this)
                .setTitle("Cancella codici errore")
                .setMessage("Sei sicuro? Questo cancellerà tutti i DTC dalla centralina.")
                .setPositiveButton("Sì, cancella", (d, w) -> {
                    showProgress(true);
                    new Thread(() -> {
                        try {
                            // Mode 04: Clear DTC — non ha PID, risposta: "44"
                            String resp = sendCommand("04", 1000);
                            mainHandler.post(() -> {
                                tvDtc.setText("DTC: Cancellati ✅");
                                showStatus("✅ Codici errore cancellati");
                                showProgress(false);
                            });
                        } catch (IOException e) {
                            mainHandler.post(() -> {
                                showStatus("❌ Errore cancellazione: " + e.getMessage());
                                showProgress(false);
                            });
                        }
                    }).start();
                })
                .setNegativeButton("Annulla", null)
                .show();
    }

    // ─── UI HELPERS ──────────────────────────────────────────────────────────

    private void updateUI(OBDData data) {
        tvRpm.setText(data.rpm >= 0
                ? "⚙️  RPM: " + data.rpm + " giri/min"
                : "⚙️  RPM: N/D");

        tvSpeed.setText(data.speedKmh >= 0
                ? "🚗  Velocità: " + data.speedKmh + " km/h"
                : "🚗  Velocità: N/D");

        tvTemp.setText(data.tempCelsius > -40
                ? "🌡️  Temperatura: " + data.tempCelsius + " °C"
                : "🌡️  Temperatura: N/D");

        tvDtc.setText("⚠️  DTC: " + data.dtcCodes);
    }

    private void showStatus(String msg) {
        tvStatus.setText(msg);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setDataButtonsEnabled(boolean enabled) {
        btnReadOnce.setEnabled(enabled);
        btnStartPolling.setEnabled(enabled);
        btnClearDtc.setEnabled(enabled);
    }

    // ─── DISCONNESSIONE ──────────────────────────────────────────────────────

    private void disconnect() {
        stopPolling();
        isConnected = false;
        closeStreams();

        showStatus("🔌 Disconnesso.");
        btnDisconnect.setEnabled(false);
        setDataButtonsEnabled(false);
        tvRpm.setText("⚙️  RPM: --");
        tvSpeed.setText("🚗  Velocità: --");
        tvTemp.setText("🌡️  Temperatura: --");
        tvDtc.setText("⚠️  DTC: --");
        tvProtocol.setText("Protocollo: --");
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
        String dtcCodes = "N/D";
    }
}
