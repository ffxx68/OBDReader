package com.ffxx68.obdreader;

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
import androidx.core.widget.NestedScrollView;

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
    private TextView tvFuelRate;
    private TextView tvProtocol;
    private TextView tvElmVersion;
    private TextView tvVin;
    private Button btnScan;
    private Button btnDisconnect;
    private Button btnReadOnce;
    private Button btnStartPolling;
    private ProgressBar progressBar;
    private TextView tvLog;
    private NestedScrollView scrollLog;
    private RadioGroup rgProtocol;

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
        tvFuelRate  = findViewById(R.id.tvFuelRate);
        tvProtocol  = findViewById(R.id.tvProtocol);
        tvElmVersion = findViewById(R.id.tvElmVersion);
        tvVin        = findViewById(R.id.tvVin);
        btnScan         = findViewById(R.id.btnScan);
        btnDisconnect   = findViewById(R.id.btnDisconnect);
        btnReadOnce     = findViewById(R.id.btnReadOnce);
        btnStartPolling = findViewById(R.id.btnStartPolling);
        progressBar     = findViewById(R.id.progressBar);
        tvLog           = findViewById(R.id.tvLog);
        scrollLog       = findViewById(R.id.scrollLog);
        rgProtocol      = findViewById(R.id.rgProtocol);

        setDataButtonsEnabled(false);
        btnDisconnect.setEnabled(false);
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth non supportato su questo dispositivo");
            btnScan.setEnabled(false);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            showStatus("Attiva il Bluetooth nelle impostazioni, poi riavvia l'app");
        } else {
            showStatus("Bluetooth attivo. Tocca 'Cerca dispositivi' per iniziare.");
        }
    }

    private void setupListeners() {
        btnScan.setOnClickListener(v -> checkPermissionsAndScan());
        btnDisconnect.setOnClickListener(v -> disconnect());
        btnReadOnce.setOnClickListener(v -> readAllDataOnce());
        btnStartPolling.setOnClickListener(v -> togglePolling());
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
                showStatus("Permessi Bluetooth negati. L'app non puo funzionare.");
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
            showStatus("Nessun dispositivo accoppiato.\n"
                    + "Vai in Impostazioni -> Bluetooth e accoppia l'ELM327 (PIN: 1234 o 6789)");
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
        showStatus("Connessione a " + device.getName() + "...");
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
                    showStatus("Connesso alla ECU");
                    showProgress(false);
                    btnScan.setEnabled(true);
                    btnDisconnect.setEnabled(true);
                    setDataButtonsEnabled(true);
                });

            } catch (EcuConnectionException e) {
                // BT connesso ma la ECU non risponde
                isConnected = true; // rimane connesso al BT, i pulsanti dati restano attivi
                mainHandler.post(() -> {
                    showStatus("Connesso a: " + device.getName()
                            + "\nErrore comunicazione ECU: " + e.getMessage()
                            + "\nVerifica protocollo o chiave in posizione ON.");
                    showProgress(false);
                    btnScan.setEnabled(true);
                    btnDisconnect.setEnabled(true);
                    setDataButtonsEnabled(true);
                });

            } catch (IOException e) {
                isConnected = false;
                mainHandler.post(() -> {
                    showStatus("Errore connessione BT: " + e.getMessage()
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
        String elmVersion = sendCommand("ATZ", 1500);  // Soft reset — risposta: "ELM327 v1.x"
        sendCommand("ATE0", 500);    // Echo OFF
        sendCommand("ATL0", 300);    // Linefeeds OFF
        sendCommand("ATS0", 300);    // Spaces OFF
        sendCommand("ATH0", 300);    // Headers OFF
        sendCommand("ATAT1", 300);   // Adaptive timing ON

        // Determina protocollo in base alla selezione UI
        int selectedId = rgProtocol.getCheckedRadioButtonId();
        String protoCmd;
        String protoLabel;

        if (selectedId == R.id.rbSP3) {
            protoCmd   = "ATSP3";
            protoLabel = "ISO 9141-2 (SP3)";
        } else if (selectedId == R.id.rbSP5) {
            protoCmd   = "ATSP5";
            protoLabel = "KWP Fast Init (SP5)";
        } else {
            protoCmd   = "ATSP0";
            protoLabel = "Auto-detect (SP0)";
        }

        sendCommand(protoCmd, 1000);

        // Verifica comunicazione con la ECU tramite Mode 01 PID 00 (PID supportati)
        String ecuTestRaw = sendCommand("0100", 5000);
        boolean ecuOk = !ecuTestRaw.isEmpty()
                && !ecuTestRaw.toUpperCase().contains("NO DATA")
                && !ecuTestRaw.toUpperCase().contains("UNABLE TO CONNECT")
                && !ecuTestRaw.toUpperCase().contains("BUS INIT")
                && !ecuTestRaw.toUpperCase().contains("ERROR")
                && !ecuTestRaw.contains("?")
                && ecuTestRaw.toUpperCase().contains("41");

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
            tvLog.append(logLine);
            // Mantieni il buffer entro MAX_LOG_LINES righe
            String current = tvLog.getText().toString();
            String[] lines = current.split("\n", -1);
            if (lines.length > MAX_LOG_LINES) {
                int excess = lines.length - MAX_LOG_LINES;
                StringBuilder trimmed = new StringBuilder();
                for (int i = excess; i < lines.length; i++) {
                    trimmed.append(lines[i]).append("\n");
                }
                tvLog.setText(trimmed.toString());
            }
            scrollLog.post(() -> scrollLog.fullScroll(NestedScrollView.FOCUS_DOWN));
        });

        return result;
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
                    showStatus("Errore lettura: " + e.getMessage());
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
        btnStartPolling.setText("Stop aggiornamento continuo");
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
                        mainHandler.post(() -> showStatus("Errore polling: " + e.getMessage()));
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
        btnStartPolling.setText("Avvia aggiornamento continuo");
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
        tvRpm.setText(data.rpm >= 0
                ? "RPM: " + data.rpm + " giri/min"
                : "RPM: N/D");

        tvSpeed.setText(data.speedKmh >= 0
                ? "Velocita: " + data.speedKmh + " km/h"
                : "Velocita: N/D");

        tvTemp.setText(data.tempCelsius > -40
                ? "Temp: " + data.tempCelsius + " C"
                : "Temp: N/D");

        float fuelMaf = calcFuelRateMaf(data.mafGps, data.speedKmh);
        float fuelSd  = calcFuelRateSpeedDensity(data.rpm, data.speedKmh, data.mapKpa, data.iatCelsius);

        String mafStr = fuelMaf > 0
                ? String.format(java.util.Locale.US, "%.1f", fuelMaf)
                : (data.mafGps >= 0 && data.speedKmh == 0 ? "fermo" : "N/D");
        String sdStr = fuelSd > 0
                ? String.format(java.util.Locale.US, "%.1f", fuelSd)
                : (data.speedKmh == 0 ? "fermo" : "N/D");

        tvFuelRate.setText("Consumo: " + mafStr + " / " + sdStr + " L/100km");
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
    }

    // ─── DISCONNESSIONE ──────────────────────────────────────────────────────

    private void disconnect() {
        stopPolling();
        isConnected = false;
        closeStreams();

        showStatus("Disconnesso.");
        btnDisconnect.setEnabled(false);
        setDataButtonsEnabled(false);
        tvRpm.setText("RPM: --");
        tvSpeed.setText("Velocita: --");
        tvTemp.setText("Temp: --");
        tvFuelRate.setText("Consumo: --");
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
}
