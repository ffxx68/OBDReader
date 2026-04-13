package com.ffxx68.obdreader;

import android.Manifest;
import android.annotation.SuppressLint;
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

    // Standard UUID for Serial Port Profile (SPP) - used by ELM327
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int READ_INTERVAL_MS = 1000; // polling interval
    private static final int MAX_LOG_LINES = 200;     // ~100 command/response exchanges
    private static final String PREFS_NAME = "OBDReaderPrefs";
    private static final String PREF_DEVICE_NAME = "selectedDeviceName";
    private static final String PREF_DEVICE_ADDRESS = "selectedDeviceAddress";
    private static final String PREF_PROTOCOL = "selectedProtocol";

    // Static variables to share data with SettingsActivity
    private static StringBuilder logBuffer = new StringBuilder();
    private static int selectedProtocol = R.id.rbAuto; // Default: Auto

    // Official PID descriptions map (SAE J1979)
    private static final Map<Integer, String> PID_DESCRIPTIONS = new HashMap<Integer, String>() {{
        // Mode 01 - PID 0x00-0x1F (Range 1)
        put(0x00, "Supported PIDs [01-20]");
        put(0x01, "DTC Monitor status");
        put(0x02, "DTC that caused freeze frame");
        put(0x03, "Fuel system status");
        put(0x04, "Calculated engine load");
        put(0x05, "Engine coolant temperature");
        put(0x06, "Short term fuel trim—Bank 1");
        put(0x07, "Long term fuel trim—Bank 1");
        put(0x08, "Short term fuel trim—Bank 2");
        put(0x09, "Long term fuel trim—Bank 2");
        put(0x0A, "Fuel pressure (gauge)");
        put(0x0B, "Intake manifold absolute pressure");
        put(0x0C, "Engine RPM");
        put(0x0D, "Vehicle speed");
        put(0x0E, "Timing advance");
        put(0x0F, "Intake air temperature");
        put(0x10, "Mass air flow (MAF)");
        put(0x11, "Throttle position");
        put(0x12, "Commanded secondary air status");
        put(0x13, "Oxygen sensors present");
        put(0x14, "O2 sensor Bank 1, Sensor 1");
        put(0x15, "O2 sensor Bank 1, Sensor 2");
        put(0x16, "O2 sensor Bank 1, Sensor 3");
        put(0x17, "O2 sensor Bank 1, Sensor 4");
        put(0x18, "O2 sensor Bank 2, Sensor 1");
        put(0x19, "O2 sensor Bank 2, Sensor 2");
        put(0x1A, "O2 sensor Bank 2, Sensor 3");
        put(0x1B, "O2 sensor Bank 2, Sensor 4");
        put(0x1C, "OBD standard");
        put(0x1D, "O2 sensors present (4 banks)");
        put(0x1E, "Auxiliary input status");
        put(0x1F, "Run time since engine start");

        // Mode 01 - PID 0x20-0x3F (Range 2)
        put(0x20, "Supported PIDs [21-40]");
        put(0x21, "Distance traveled with MIL on");
        put(0x22, "Fuel rail pressure relative");
        put(0x23, "Fuel rail gauge pressure");
        put(0x24, "O2 sensor equivalence ratio 1");
        put(0x25, "O2 sensor equivalence ratio 2");
        put(0x26, "O2 sensor equivalence ratio 3");
        put(0x27, "O2 sensor equivalence ratio 4");
        put(0x28, "O2 sensor equivalence ratio 5");
        put(0x29, "O2 sensor equivalence ratio 6");
        put(0x2A, "O2 sensor equivalence ratio 7");
        put(0x2B, "O2 sensor equivalence ratio 8");
        put(0x2C, "Commanded EGR");
        put(0x2D, "EGR Error");
        put(0x2E, "Commanded evaporative purge");
        put(0x2F, "Fuel tank level");
        put(0x30, "Warm-ups since DTC cleared");
        put(0x31, "Distance since DTC cleared");
        put(0x32, "Evap system vapor pressure");
        put(0x33, "Absolute barometric pressure");
        put(0x34, "O2 sensor current 1");
        put(0x35, "O2 sensor current 2");
        put(0x36, "O2 sensor current 3");
        put(0x37, "O2 sensor current 4");
        put(0x38, "O2 sensor current 5");
        put(0x39, "O2 sensor current 6");
        put(0x3A, "O2 sensor current 7");
        put(0x3B, "O2 sensor current 8");
        put(0x3C, "Catalyst temperature Bank 1, Sensor 1");
        put(0x3D, "Catalyst temperature Bank 2, Sensor 1");
        put(0x3E, "Catalyst temperature Bank 1, Sensor 2");
        put(0x3F, "Catalyst temperature Bank 2, Sensor 2");

        // Mode 01 - PID 0x40-0x5F (Range 3)
        put(0x40, "Supported PIDs [41-60]");
        put(0x41, "Monitor status this drive cycle");
        put(0x42, "Control module voltage");
        put(0x43, "Absolute engine load value");
        put(0x44, "Commanded air-fuel equivalence ratio");
        put(0x45, "Relative throttle position");
        put(0x46, "Ambient air temperature");
        put(0x47, "Absolute throttle position B");
        put(0x48, "Absolute throttle position C");
        put(0x49, "Accelerator pedal position D");
        put(0x4A, "Accelerator pedal position E");
        put(0x4B, "Accelerator pedal position F");
        put(0x4C, "Commanded throttle actuator");
        put(0x4D, "Time with MIL on");
        put(0x4E, "Time since DTC cleared");
        put(0x4F, "Maximum sensor values (multiple)");
        put(0x50, "Maximum MAF air flow rate");
        put(0x51, "Fuel type");
        put(0x52, "Ethanol fuel percentage");
        put(0x53, "Absolute EVAP vapor pressure");
        put(0x54, "EVAP vapor pressure");
        put(0x55, "Short term sec. O2 trim Bank 1/3");
        put(0x56, "Long term sec. O2 trim Bank 1/3");
        put(0x57, "Short term sec. O2 trim Bank 2/4");
        put(0x58, "Long term sec. O2 trim Bank 2/4");
        put(0x59, "Absolute fuel rail pressure");
        put(0x5A, "Relative accelerator pedal position");
        put(0x5B, "Hybrid battery pack level");
        put(0x5C, "Engine oil temperature");
        put(0x5D, "Fuel injection timing");
        put(0x5E, "Engine fuel rate");
        put(0x5F, "Emission requirements");

        // Mode 01 - PID 0x60-0x7F (Range 4)
        put(0x60, "Supported PIDs [61-80]");
        put(0x61, "Driver demand torque");
        put(0x62, "Actual engine torque");
        put(0x63, "Engine reference torque");
        put(0x64, "Engine percent torque data");
        put(0x65, "Auxiliary input/output");
        put(0x66, "Mass air flow sensor");
        put(0x67, "Engine coolant temperature");
        put(0x68, "Intake air temperature sensor");
        put(0x69, "Commanded EGR and EGR error");
        put(0x6A, "Commanded Diesel intake air flow");
        put(0x6B, "Exhaust gas recirculation temp");
        put(0x6C, "Commanded throttle actuator");
        put(0x6D, "Fuel pressure control system");
        put(0x6E, "Injection pressure control system");
        put(0x6F, "Turbocharger compressor inlet pressure");
        put(0x70, "Boost pressure control");
        put(0x71, "Variable Geometry turbo control");
        put(0x72, "Wastegate control");
        put(0x73, "Exhaust pressure");
        put(0x74, "Turbocharger RPM");
        put(0x75, "Turbocharger temperature");
        put(0x76, "Turbocharger temperature");
        put(0x77, "Charge air cooler temperature");
        put(0x78, "Exhaust Gas temperature Bank 1");
        put(0x79, "Exhaust Gas temperature Bank 2");
        put(0x7A, "Diesel particulate filter Bank 1");
        put(0x7B, "Diesel particulate filter Bank 2");
        put(0x7C, "Diesel Particulate filter temperature");
        put(0x7D, "NOx NTE control area status");
        put(0x7E, "PM NTE control area status");
        put(0x7F, "Engine run time");

        // Mode 01 - PID 0x80-0x9F (Range 5)
        put(0x80, "PIDs supportati [81-A0]");
        put(0x81, "Engine run time AECD");
        put(0x82, "Engine run time AECD");
        put(0x83, "NOx sensor");
        put(0x84, "Manifold surface temperature");
        put(0x85, "NOx reagent system");
        put(0x86, "Particulate matter sensor");
        put(0x87, "Intake manifold absolute pressure");
        put(0x88, "SCR Induce System");
        put(0x89, "Run Time for AECD #11-#15");
        put(0x8A, "Run Time for AECD #16-#20");
        put(0x8B, "Diesel Aftertreatment");
        put(0x8C, "O2 Sensor (Wide Range)");
        put(0x8D, "Throttle Position G");
        put(0x8E, "Engine Friction - Percent Torque");
        put(0x8F, "PM Sensor Bank 1 & 2");
        put(0x90, "WWH-OBD Vehicle OBD System Info");
        put(0x91, "WWH-OBD Vehicle OBD System Info");
        put(0x92, "Fuel System Control");
        put(0x93, "WWH-OBD Vehicle OBD Counters");
        put(0x94, "NOx Warning And Inducement System");
        put(0x98, "Exhaust Gas Temperature Sensor");
        put(0x99, "Exhaust Gas Temperature Sensor");
        put(0x9A, "Hybrid/EV Vehicle System Data, Battery, Voltage");
        put(0x9B, "Diesel Exhaust Fluid Sensor Data");
        put(0x9C, "O2 Sensor Data");
        put(0x9D, "Engine Fuel Rate");
        put(0x9E, "Engine Exhaust Flow Rate");
        put(0x9F, "Fuel System Percentage Use");

        // Mode 01 - PID 0xA0-0xBF (Range 6)
        put(0xA0, "PIDs supportati [A1-C0]");
        put(0xA1, "NOx Sensor Corrected Data");
        put(0xA2, "Cylinder Fuel Rate");
        put(0xA3, "Evap System Vapor Pressure");
        put(0xA4, "Transmission Actual Gear");
        put(0xA5, "Commanded Diesel Exhaust Fluid Dosing");
        put(0xA6, "Odometer");

        // Mode 01 - PID 0xC0-0xDF (Range 7)
        put(0xC0, "PIDs supportati [C1-E0]");
        put(0xC3, "Fuel Rate (Multi)");
        put(0xC4, "Fuel Rate (Multi)");
    }};

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
    private static final int MAX_BT_ERRORS_BEFORE_RECONNECT = 3;  // Max retry connessione BT prima di dare errore
    private static final int MAX_NO_DATA_BEFORE_RETRY = 3; // Max retry prima del re-init ECU
    private static final int MAX_NO_DATA_BEFORE_CLOSE_TRIP = 150; // Max retry prima di chiudere il viaggio

    // Handler per aggiornamenti UI dal thread BT
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;

    // UI Elements
    private TextView tvStatus;
    private TextView tvRpm;
    private TextView tvSpeed;
    private TextView tvTemp;
    private TextView tvFuelRateInstantMaf;
    private TextView tvFuelFlowMaf;  // L/h MAF
    private TextView tvTotalKm;
    private TextView tvAvgSpeed;
    private TextView tvAvgFuelMaf;
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
    private long lastUpdateTimeMs = 0;

    // Gestione log viaggi
    private TripLogManager tripLogManager;
    private TripLog currentTrip;

    // Lista dei PID supportati dalla modalità 01
    // Supporta fino a PID 0xE0 (224) tramite query multiple (0100, 0120, 0140, ecc.)
    private Set<Integer> supportedPids = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
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
        tvFuelFlowMaf = findViewById(R.id.tvFuelFlowMaf);  // L/s MAF
        tvTotalKm   = findViewById(R.id.tvTotalKm);
        tvAvgSpeed  = findViewById(R.id.tvAvgSpeed);
        tvAvgFuelMaf = findViewById(R.id.tvAvgFuelMaf);
        tvElmVersion = findViewById(R.id.tvElmVersion);
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

    @SuppressLint("MissingPermission")
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
                    btnConnect.setEnabled(false);
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
                    btnConnect.setEnabled(false);
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

        // Raccolta completa dei PID supportati dalla ECU
        supportedPids.clear();
        boolean ecuOk = queryAllSupportedPids();

        // Non leggiamo più il VIN per semplificare l'interfaccia
        final String label   = protoLabel;
        final String elmVer  = elmVersion.isEmpty() ? "?" : elmVersion;
        mainHandler.post(() -> {
            tvElmVersion.setText("ELM327: " + elmVer + " | Protocollo: " + label);
        });

        if (!ecuOk) {
            throw new EcuConnectionException("ECU non risponde o protocollo incompatibile");
        }
    }

    /** Eccezione specifica per errori di comunicazione con la ECU (distinta da errori BT). */
    static class EcuConnectionException extends IOException {
        EcuConnectionException(String message) { super(message); }
    }

    /**
     * Queries the ECU to collect all supported PIDs.
     * The OBD-II protocol organizes PIDs in blocks of 32 (0x20):
     *   - 0100: PID 01-20 (hex 0x01-0x20)
     *   - 0120: PID 21-40 (hex 0x21-0x40)
     *   - 0140: PID 41-60 (hex 0x41-0x60)
     *   - 0160: PID 61-80 (hex 0x61-0x80)
     *   - 0180: PID 81-A0 (hex 0x81-0xA0)
     *   - 01A0: PID A1-C0 (hex 0xA1-0xC0)
     *   - 01C0: PID C1-E0 (hex 0xC1-0xE0)
     *
     * Each response contains 4 bytes (32 bits), where each bit represents
     * support for a specific PID in the range. Bit 0 (LSB of last byte) always
     * indicates if the next range is supported.
     *
     * @return true if ECU responds correctly, false otherwise
     */
    private boolean queryAllSupportedPids() throws IOException {
        // Array of query PIDs for each range
        int[] queryPids = {0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0};

        boolean ecuResponding = false;

        for (int queryPid : queryPids) {
            String command = String.format("01%02X", queryPid);
            String response = sendCommand(command, 500);

            // Check if response is valid
            if (!isValidEcuResponse(response)) {
                // If it's the first query (0100) and fails, ECU is not responding
                if (queryPid == 0x00) {
                    return false;
                }
                // For subsequent queries, if they fail there are simply no more PIDs
                break;
            }

            ecuResponding = true;

            // Extract supported PIDs from this response
            Set<Integer> pidsInRange = parseSupportedPids(response, queryPid);
            supportedPids.addAll(pidsInRange);

            // If bit 0 (PID 0x20 in next range) is not set, there are no more ranges
            if (!pidsInRange.contains(queryPid + 0x20)) {
                break;
            }
        }

        // Log supported PIDs for debug
        if (ecuResponding && !supportedPids.isEmpty()) {
            StringBuilder pidList = new StringBuilder("PIDs supported by ECU:\n");
            List<Integer> sortedPids = new ArrayList<>(supportedPids);
            Collections.sort(sortedPids);
            for (int pid : sortedPids) {
                String description = PID_DESCRIPTIONS.get(pid);
                if (description != null) {
                    pidList.append(String.format("  %02X - %s\n", pid, description));
                } else {
                    pidList.append(String.format("  %02X - (unknown)\n", pid));
                }
            }
            final String logMsg = pidList.toString();
            mainHandler.post(() -> {
                logBuffer.append(">> ").append(logMsg).append("\n");
            });
        }

        return ecuResponding;
    }

    /**
     * Checks if the ECU response is valid.
     */
    private boolean isValidEcuResponse(String response) {
        if (response == null || response.isEmpty()) {
            return false;
        }
        String upper = response.toUpperCase();

        // Check error conditions
        if (upper.contains("NO DATA") ||
            upper.contains("UNABLE TO CONNECT") ||
            upper.contains("ERROR") ||
            upper.contains("?")) {
            return false;
        }

        // Check if there's BUS INIT without OK (error)
        boolean hasBusInit = upper.contains("BUS INIT");
        boolean hasOk = upper.contains("OK");
        if (hasBusInit && !hasOk) {
            return false;
        }

        return true;
    }

    /**
     * Extracts supported PIDs from the response to 01XX command.
     *
     * @param response Raw response from ECU
     * @param basePid Query PID (0x00, 0x20, 0x40, etc.)
     * @return Set of supported PIDs in this range
     */
    private Set<Integer> parseSupportedPids(String response, int basePid) {
        Set<Integer> pids = new HashSet<>();

        String clean = response.replaceAll("\\s+", "").toUpperCase();

        // Find the part after OK if present
        int okIdx = clean.indexOf("OK");
        if (okIdx >= 0) {
            clean = clean.substring(okIdx + 2);
        }

        // Find header 41XX (response to mode 01, PID XX)
        String header = String.format("41%02X", basePid);
        int idx = clean.indexOf(header);

        if (idx < 0 || clean.length() < idx + header.length() + 8) {
            return pids; // Invalid response
        }

        // Extract 8 hex characters (4 bytes) after header
        String payload = clean.substring(idx + header.length(), idx + header.length() + 8);

        try {
            // Convert to 4-byte array
            int[] bytes = new int[4];
            for (int i = 0; i < 4; i++) {
                bytes[i] = Integer.parseInt(payload.substring(i * 2, i * 2 + 2), 16);
            }

            // Each bit represents a supported PID
            // Bit 7 of byte 0 = PID basePid+1
            // Bit 6 of byte 0 = PID basePid+2
            // ...
            // Bit 0 of byte 3 = PID basePid+32
            for (int i = 0; i < 32; i++) {
                int byteIdx = i / 8;
                int bitIdx = 7 - (i % 8); // MSB first

                if (((bytes[byteIdx] >> bitIdx) & 0x01) == 1) {
                    int pidValue = basePid + i + 1;
                    pids.add(pidValue);
                }
            }
        } catch (NumberFormatException e) {
            // Invalid payload, return empty set
        }

        return pids;
    }

    /**
     * Extracts ASCII VIN from raw 0902 response.
     * ELM327 response format (headers off, spaces off):
     *   490201 + 11 hex bytes of ASCII VIN
     * Some vehicles respond on multiple lines: "4902 01 XX XX XX..."
     */
    private String parseVin(String raw) {
        if (raw == null || raw.isEmpty() || raw.contains("NO DATA")
                || raw.contains("ERROR") || raw.contains("?")) {
            return "N/A";
        }
        // Remove spaces and find marker "490201" or "49 02 01"
        String hex = raw.replaceAll("\\s+", "").toUpperCase();
        int idx = hex.indexOf("490201");
        if (idx < 0) idx = hex.indexOf("4902");
        if (idx < 0) return "N/A";
        // Skip header bytes (490201 = 6 chars)
        String vinHex = hex.substring(idx + 6);
        // Convert hex bytes to ASCII characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < vinHex.length() && sb.length() < 17; i += 2) {
            try {
                int b = Integer.parseInt(vinHex.substring(i, i + 2), 16);
                if (b >= 0x20 && b <= 0x7E) sb.append((char) b);
            } catch (NumberFormatException ignored) {}
        }
        return sb.length() > 0 ? sb.toString() : "N/A";
    }

    // ─── OBD-II COMMANDS ─────────────────────────────────────────────────

    /**
     * Sends an AT or OBD-II command to ELM327 and reads the response.
     * The terminator '\r' is required by ELM327.
     * The chip responds with '>' (prompt) when ready for the next command.
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
                if (c == '>') break; // ELM327 ready for next command
                response.append(c);
            } else {
                try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); };
            }
        }
        String result = response.toString().replace("\r", "").replace("\n", " ").trim();

        // Build log with PID description if applicable
        String logLine = ">> " + command + "\n<< " + result + "\n";
        if (command.length() == 4 && command.startsWith("01")) {
            // It's a Mode 01 command - extract PID and find description
            try {
                int pid = Integer.parseInt(command.substring(2), 16);
                String description = PID_DESCRIPTIONS.get(pid);
                if (description != null) {
                    logLine = ">> " + command + " (" + description + ")\n<< " + result + "\n";
                }
            } catch (NumberFormatException ignored) {
                // Keep default value
            }
        }

        final String finalLogLine = logLine;
        mainHandler.post(() -> {
            // Add to static buffer
            logBuffer.append(finalLogLine);

            // Keep buffer within MAX_LOG_LINES lines
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

    // Overload without custom sleep
    private String sendCommand(String command) throws IOException {
        return sendCommand(command, 100);
    }

    // ─── DATA READING ────────────────────────────────────────────────────

    private void startPolling() {
        if (isPolling) return; // Already polling

        isPolling = true;

        // Reset trip statistics ONLY if it's a new connection (not a reconnection)
        if (currentTrip == null || totalDistanceKm == 0) {
            totalDistanceKm = 0.0;
            totalFuelMafLiters = 0.0;
        }
        lastUpdateTimeMs = System.currentTimeMillis();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;

                new Thread(() -> {
                    try {
                        OBDData data = fetchOBDData();
                        CalculatedData calc = calculateDerivedData(data);
                        updateCurrentTrip(calc);
                        consecutiveBTErrors = 0;
                        consecutiveNoData = 0;
                        mainHandler.post(() -> {
                            updateUI(data, calc);
                            if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                        });

                    } catch (EcuNoDataException e) {
                        // ECU not providing data (engine probably off)
                        consecutiveNoData++;
                        consecutiveBTErrors = 0; // Not a BT connection error

                        mainHandler.post(() -> {
                            if (consecutiveNoData >= MAX_NO_DATA_BEFORE_CLOSE_TRIP) {
                                // Engine off too long: close trip
                                showStatus("Engine off. Closing trip...");

                                // Close trip if active
                                if (currentTrip != null && totalDistanceKm > 0.01) {
                                    currentTrip.endTrip(totalDistanceKm, calculateAverageSpeed(), calculateAverageFuelConsumption());
                                    tripLogManager.updateCurrentTrip(currentTrip);
                                    currentTrip = null;
                                }

                                stopPolling();
                                if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);

                            } else if (consecutiveNoData == MAX_NO_DATA_BEFORE_RETRY && shouldStayConnected) {
                                // Try to re-initialize ECU (only on exact MAX_NO_DATA_BEFORE_RETRY)
                                showStatus("ECU not responding. Waiting for engine restart... (" + consecutiveNoData + ")");

                                // Try to re-initialize ELM327/ECU
                                new Thread(() -> {
                                    try {
                                        initElm327();
                                        mainHandler.post(() -> {
                                            consecutiveNoData = 0; // Reset in UI thread to avoid race conditions
                                            showStatus("ECU detected! Resuming data reading.");
                                        });
                                    } catch (Exception initError) {
                                        // Re-init failed, keep waiting
                                        mainHandler.post(() -> {
                                            showStatus("Engine off. Waiting... (" + consecutiveNoData + "/" + MAX_NO_DATA_BEFORE_CLOSE_TRIP + ")");
                                        });
                                    }
                                    if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                                }).start();
                            } else {
                                showStatus("Engine off? Attempt " + consecutiveNoData + "/" + MAX_NO_DATA_BEFORE_CLOSE_TRIP);
                                if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                            }
                        });

                    } catch (IOException e) {
                        // Bluetooth connection error
                        consecutiveBTErrors++;
                        mainHandler.post(() -> {
                            if (consecutiveBTErrors >= MAX_BT_ERRORS_BEFORE_RECONNECT && shouldStayConnected) {
                                // Automatic reconnection attempt (BT lost)
                                showStatus("BT connection lost. Reconnecting...");
                                isConnected = false;
                                closeStreams();

                                // Retry connection after 2 seconds
                                mainHandler.postDelayed(() -> {
                                    if (shouldStayConnected) {
                                        connectToSavedDevice();
                                    }
                                }, 2000);
                            } else if (consecutiveBTErrors < MAX_BT_ERRORS_BEFORE_RECONNECT) {
                                // Show error but keep trying
                                showStatus("Read error (" + consecutiveBTErrors + "/" + MAX_BT_ERRORS_BEFORE_RECONNECT + "): " + e.getMessage());
                                if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                            } else {
                                // Too many errors and should not reconnect: close trip
                                showStatus("Connection lost: " + e.getMessage());
                                stopPolling();

                                // Close trip if active
                                if (currentTrip != null && totalDistanceKm > 0.01) {
                                    currentTrip.endTrip(totalDistanceKm, calculateAverageSpeed(), calculateAverageFuelConsumption());
                                    tripLogManager.updateCurrentTrip(currentTrip);
                                    currentTrip = null;
                                }
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
        // Aggiorna viaggio se necessario
        updateCurrentTrip(null);
    }

    /**
     * Retrieves all OBD-II data from the vehicle.
     * PIDs (Parameter IDs) are defined by SAE J1979 standard.
     *
     * Response format: "41 XX AA BB" where:
     *   41 = response to mode 01
     *   XX = requested PID
     *   AA, BB = data bytes
     */
    private OBDData fetchOBDData() throws IOException {
        OBDData data = new OBDData();

        // RPM — PID 0x0C — Formula: ((A*256)+B)/4 → rpm
        String rpmRaw = sendCommand("010C");
        data.rpm = parseRpm(rpmRaw);

        // Check if ECU is responding with valid data
        if (rpmRaw.toUpperCase().contains("NO DATA") ||
            rpmRaw.toUpperCase().contains("UNABLE TO CONNECT") ||
            rpmRaw.toUpperCase().contains("STOPPED") ||
            data.rpm < 0) {
            throw new EcuNoDataException("ECU not providing data (engine off?)");
        }

        // Speed — PID 0x0D — Formula: A → km/h (direct value)
        String speedRaw = sendCommand("010D");
        data.speedKmh = parseSpeed(speedRaw);

        // Calculated engine load — PID 0x04 — Formula: A*100/255 → % (0-100)
        String loadRaw = sendCommand("0104");
        data.engineLoad = parseEngineLoad(loadRaw);

        // Engine coolant temperature — PID 0x05 — Formula: A-40 → °C
        String tempRaw = sendCommand("0105");
        data.tempCelsius = parseTemp(tempRaw);

        // MAF — PID 0x10 — Formula: ((A*256)+B)/100 → g/s
        String mafRaw = sendCommand("0110");
        data.mafGps = parseMaf(mafRaw);

        // Intake Air Temperature (IAT) — PID 0x0F — Formula: A-40 → °C
        String iatRaw = sendCommand("010F");
        data.iatCelsius = parseIAT(iatRaw);

        // Manifold Absolute Pressure (MAP) — PID 0x0B — Formula: A → kPa
        String mapRaw = sendCommand("010B");
        data.mapKpa = parseMAP(mapRaw);

        // Barometric Pressure — PID 0x33 — Formula: A → kPa
        String baroRaw = sendCommand("0133");
        data.baroKpa = parseBaro(baroRaw);

        // Throttle Position — PID 0x11 — Formula: A*100/255 → % (0-100)
        String throttleRaw = sendCommand("0111");
        data.throttlePosition = parseThrottlePosition(throttleRaw);

        return data;
    }

    /** Exception for when ECU does not provide data (e.g., engine off) */
    static class EcuNoDataException extends IOException {
        EcuNoDataException(String message) { super(message); }
    }

    // ─── OBD-II RESPONSE PARSERS ─────────────────────────────────────────

    /**
     * Extracts hex bytes from ELM327 response.
     * Example response: "410C1A2B" → header="410C" → bytes=[0x1A, 0x2B]
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

    private int parseEngineLoad(String raw) {
        List<Integer> b = extractBytes(raw, "4104");
        if (b.isEmpty()) return -1;
        return (b.get(0) * 100) / 255; // Convert 0-255 to 0-100%
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

    private int parseIAT(String raw) {
        // PID 0x0F - Intake Air Temperature: A-40 → °C
        List<Integer> b = extractBytes(raw, "410F");
        return b.isEmpty() ? -41 : b.get(0) - 40;
    }

    private int parseMAP(String raw) {
        // PID 0x0B - Manifold Absolute Pressure: A → kPa
        List<Integer> b = extractBytes(raw, "410B");
        return b.isEmpty() ? -1 : b.get(0);
    }

    private int parseBaro(String raw) {
        // PID 0x33 - Barometric Pressure: A → kPa
        List<Integer> b = extractBytes(raw, "4133");
        return b.isEmpty() ? -1 : b.get(0);
    }

    private int parseThrottlePosition(String raw) {
        // PID 0x11 - Throttle Position: A*100/255 → % (0-100)
        List<Integer> b = extractBytes(raw, "4111");
        if (b.isEmpty()) return -1;
        return (b.get(0) * 100) / 255; // Convert 0-255 to 0-100%
    }


    /**
     * Calculates the effective base AFR (Air-Fuel Ratio) for diesel engines based on load.
     *
     * CORRECTED: Diesel engines vary AFR inversely with load:
     * - Low load/Idle (0-20%): AFR ≈ 15:1 - 18:1 (stoichiometric, richer)
     * - Medium load (20-60%): AFR ≈ 18:1 - 25:1
     * - High load (60-100%): AFR ≈ 25:1 - 35:1 (lean, excess air for power)
     *
     * This is the OPPOSITE of what was implemented before. At idle with little air flow,
     * the engine injects relatively MORE fuel (lower AFR ratio). As load increases,
     * the engine operates leaner (higher AFR) for efficiency.
     *
     * @param engineLoad Engine load in % (0-100), -1 if not available
     * @return Effective base AFR to use in calculations
     */
    private float calculateBaseAFR(int engineLoad) {
        // If load not available, use average value for normal driving
        if (engineLoad < 0) {
            return 22.0f; // Safe average value
        }

        // Limit load to 0-100 range
        int load = Math.max(0, Math.min(100, engineLoad));

        // Linear interpolation for AFR based on load - INVERTED
        // Minimum AFR (minimum load/idle) = 15.0:1 (stoichiometric, richer)
        // Maximum AFR (maximum load) = 35.0:1 (lean)
        // Formula: AFR = 15.0 + (load * (35.0 - 15.0) / 100)
        float afrMin = 15.0f;  // At idle (load=0)
        float afrMax = 35.0f;  // At full load (load=100)

        return afrMin + (load * (afrMax - afrMin) / 100.0f);
    }

    /**
     * Calculates AFR (Air-Fuel Ratio) with 2D table (Load × RPM).
     *
     * CORRECTED: Diesel engines require LOWER AFR (richer mixture) at idle/low load
     * because the combustion needs more fuel relative to the small amount of air.
     *
     * Actual AFR behavior in diesel:
     * - Idle/Low load (0-20%): AFR ≈ 15:1 - 18:1 (stoichiometric range)
     * - Medium load (20-60%): AFR ≈ 18:1 - 25:1
     * - High load (60-100%): AFR ≈ 25:1 - 35:1 (lean, more excess air)
     *
     * @param engineLoad Engine load in % (0-100), -1 if not available
     * @param rpm Engine RPM, -1 if not available
     * @return Effective AFR optimized for load and RPM
     */
    private float calculateDieselAFR(int engineLoad, int rpm) {
        // Calculate base AFR from load
        float baseAFR = calculateBaseAFR(engineLoad);

        // If RPM not available, use base AFR only
        if (rpm < 0) {
            return baseAFR;
        }

        // Correction factor based on RPM
        // Lower AFR at low RPM (richer mixture for combustion efficiency)
        // Higher AFR at high RPM (leaner mixture for efficiency)
        float rpmFactor;
        if (rpm < 2000) {
            // Low RPM: -5% AFR (richer mixture for combustion)
            rpmFactor = 0.95f;
        } else if (rpm > 3500) {
            // High RPM: +5% AFR (leaner mixture for efficiency)
            rpmFactor = 1.05f;
        } else {
            // Medium RPM: standard AFR
            rpmFactor = 1.0f;
        }

        return baseAFR * rpmFactor;
    }

    /**
     * Calculates improved AFR with corrections for temperature, air pressure and throttle.
     *
     * Applied corrections:
     * 1. Air temperature (IAT): cold air is denser → more oxygen available
     * 2. Manifold pressure (MAP): includes turbo and altitude effect
     * 3. Barometric pressure (BARO): compensates for altitude variations
     * 4. Throttle Position (TPS): correlation with engine load for validation
     *
     * @param engineLoad Engine load 0-100%
     * @param rpm Engine RPM
     * @param iatCelsius Intake air temperature (°C), -41 if not available
     * @param mapKpa Manifold pressure (kPa), -1 if not available
     * @param baroKpa Barometric pressure (kPa), -1 if not available
     * @param throttlePosition Throttle position 0-100%, -1 if not available
     * @return AFR corrected for real conditions
     */
    private float calculateEnhancedAFR(int engineLoad, int rpm, int iatCelsius, int mapKpa, int baroKpa, int throttlePosition) {
        // Base AFR from load and RPM
        float afr = calculateDieselAFR(engineLoad, rpm);

        // NOTE: Temperature and pressure corrections were REMOVED
        // Reason: MAF sensor already compensates for air density (temperature, altitude).
        // Multiplying AFR by pressure/temperature factors was WRONG because:
        // 1. AFR is a mass ratio (fuel mass / air mass), independent of pressure/temperature
        // 2. Changes in air density are already reflected in MAF reading
        // 3. Applying both corrections = double-counting the effect

        // Throttle Position correction (optional but useful for validation)
        // In a diesel, low throttle with high load indicates acceleration/boost
        // High throttle with low load may indicate engine braking or downhill
        if (throttlePosition >= 0 && engineLoad >= 0) {
            // Difference between throttle and load indicates special conditions
            int throttleLoadDiff = throttlePosition - engineLoad;

            if (throttleLoadDiff > 30) {
                // Throttle much higher than load: likely engine braking or downhill
                // ECU injects less fuel → higher AFR
                afr *= 1.1f; // +10% AFR (leaner mixture)
            }
            // Note: if throttleLoadDiff < -30 with engineLoad > 50, indicates turbo boost
            // but AFR is already corrected by MAP correction, so no action needed
        }

        return afr;
    }

    /**
     * Calculates fuel flow rate in L/h from MAF with improved AFR.
     *
     * @param mafGps Air flow in g/s from MAF sensor
     * @param engineLoad Engine load 0-100%
     * @param rpm Engine RPM
     * @param iatCelsius Intake air temperature
     * @param mapKpa Manifold pressure
     * @param baroKpa Barometric pressure
     * @param throttlePosition Throttle position 0-100%
     * @return Fuel flow rate in L/h, -1 if invalid data
     */
    private float calcFuelFlowMaf(float mafGps, int engineLoad, int rpm, int iatCelsius, int mapKpa, int baroKpa, int throttlePosition) {
        if (mafGps < 0) return -1f;

        // Calculate improved AFR with temperature, pressure and throttle corrections
        float afr = calculateEnhancedAFR(engineLoad, rpm, iatCelsius, mapKpa, baroKpa, throttlePosition);

        // Fuel flow rate in L/s, then convert to L/h
        float litersPerSec = mafGps / afr / 840f;
        return litersPerSec * 3600f;  // Convert L/s → L/h
    }

    /**
     * Calculates instantaneous consumption in L/100km from MAF and speed.
     * Formula: L/100km = (L/s * 3600) / (km/h) * 100
     *   MAF from OBD-II PID 0x10 is in g/s (grams per second)
     *   Variable diesel AFR based on engine load and RPM (2D table)
     *   Diesel density ≈ 840 g/L
     *
     * NOTE: Diesel engines run with excess air (lean mixture).
     * Effective AFR varies based on engine load and RPM:
     * - Low load + low RPM: high AFR (~30-34:1)
     * - Medium load + medium RPM: medium AFR (~22-27:1)
     * - High load + high RPM: low AFR (~14-18:1)
     *
     * Derivation:
     *   1. Calculate AFR based on load and RPM: AFR = f(engineLoad, rpm)
     *   2. Convert MAF to fuel rate: fuelRate_g/s = MAF_g/s / AFR
     *   3. Convert to L/s: fuelRate_L/s = fuelRate_g/s / density_g/L
     *   4. Convert to L/h: fuelRate_L/h = fuelRate_L/s * 3600
     *   5. Calculate L/100km: (fuelRate_L/h / speed_km/h) * 100
     */
    private float calcFuelRateMaf(float mafGps, int speedKmh, int engineLoad, int rpm, int iatCelsius, int mapKpa, int baroKpa, int throttlePosition) {
        if (mafGps < 0 || speedKmh <= 0) return -1f;

        float litersPerHour = calcFuelFlowMaf(mafGps, engineLoad, rpm, iatCelsius, mapKpa, baroKpa, throttlePosition);
        if (litersPerHour < 0) return -1f;

        return (litersPerHour / speedKmh) * 100f;
    }

    // ─── UI HELPERS ──────────────────────────────────────────────────────

    private void updateUI(OBDData data, CalculatedData calc) {
        // RPM
        tvRpm.setText(data.rpm >= 0
                ? data.rpm + " rpm"
                : "N/A");

        // Speed
        tvSpeed.setText(data.speedKmh >= 0
                ? data.speedKmh + " km/h"
                : "N/A");

        // Temperature
        tvTemp.setText(data.tempCelsius > -40
                ? data.tempCelsius + " C"
                : "N/A");

        // Display instantaneous km/L
        tvFuelRateInstantMaf.setText(calc.kmLMaf > 0
                ? String.format(java.util.Locale.US, "%.2f", calc.kmLMaf)
                : (data.speedKmh == 0 ? "stopped" : "N/A"));

        // Display fuel flow rate in L/h (liters per hour)
        tvFuelFlowMaf.setText(calc.fuelFlowMafLh > 0
                ? String.format(java.util.Locale.US, "%.2f", calc.fuelFlowMafLh)
                : "N/A");

        // Display traveled km
        tvTotalKm.setText(String.format(java.util.Locale.US, "%.2f", calc.totalDistanceKm));

        // Average speed
        if (calc.avgSpeed > 0) {
            tvAvgSpeed.setText(String.format(java.util.Locale.US, "%.1f km/h", calc.avgSpeed));
        } else {
            tvAvgSpeed.setText("--");
        }

        // Average km/L
        if (calc.avgKmLMaf > 0) {
            tvAvgFuelMaf.setText(String.format(java.util.Locale.US, "%.2f", calc.avgKmLMaf));
        } else {
            tvAvgFuelMaf.setText("--");
        }
    }

    // ─── CALCULATIONS ─────────────────────────────────────────────────────

    private CalculatedData calculateDerivedData(OBDData data) {
        CalculatedData calc = new CalculatedData();

        // Instant Fuel consumption
        calc.fuelMafL100 = calcFuelRateMaf(data.mafGps, data.speedKmh, data.engineLoad, data.rpm,
                data.iatCelsius, data.mapKpa, data.baroKpa, data.throttlePosition);
        calc.fuelFlowMafLh = calcFuelFlowMaf(data.mafGps, data.engineLoad, data.rpm,
                data.iatCelsius, data.mapKpa, data.baroKpa, data.throttlePosition);
        calc.kmLMaf = (calc.fuelMafL100 > 0) ? (100.0f / calc.fuelMafL100) : -1f;

        // Trip statistics (distance, fuel, average speed/consumption)
        if (data.speedKmh > 0) {
            long currentTimeMs = System.currentTimeMillis();
            if (lastUpdateTimeMs > 0) {
                double elapsedHours = (currentTimeMs - lastUpdateTimeMs) / 3600000.0; // ms to hours
                double distanceKm = data.speedKmh * elapsedHours;
                totalDistanceKm += distanceKm;
                if (calc.fuelFlowMafLh > 0) {
                    totalFuelMafLiters += calc.fuelFlowMafLh * elapsedHours;
                }
            }
            lastUpdateTimeMs = System.currentTimeMillis();
        }
        calc.totalDistanceKm = totalDistanceKm;
        calc.totalFuelMafLiters = totalFuelMafLiters;
        calc.avgSpeed = calculateAverageSpeed();
        calc.avgKmLMaf = calculateAverageFuelConsumption();

        return calc;
    }

    private void showStatus(String msg) {
        tvStatus.setText(msg);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }


    /**
     * Calculates average speed as total km / total time in hours
     * @return average speed in km/h, or 0.0 if not calculable
     */
    private double calculateAverageSpeed() {
        if (currentTrip == null || totalDistanceKm <= 0.01) {
            return 0.0;
        }
        long elapsedTimeMs = System.currentTimeMillis() - currentTrip.getStartTime();
        double elapsedTimeHours = elapsedTimeMs / 3600000.0;
        return elapsedTimeHours > 0.001 ? totalDistanceKm / elapsedTimeHours : 0.0;
    }

    /**
     * Calculates average consumption as total km / total liters
     * @return average consumption in km/L, or 0.0 if not calculable
     */
    private double calculateAverageFuelConsumption() {
        return (totalDistanceKm > 0.01 && totalFuelMafLiters > 0)
            ? totalDistanceKm / totalFuelMafLiters
            : 0.0;
    }

    /**
     * Update current trip with latest calculated data.
     */
    private void updateCurrentTrip(CalculatedData calc) {
        if (currentTrip != null && totalDistanceKm > 0) {
            currentTrip.updateTrip(calc);
            tripLogManager.updateCurrentTrip(currentTrip);
        }
    }


    // ─── DISCONNECTION ───────────────────────────────────────────────────

    private void disconnect() {
        shouldStayConnected = false; // Disable automatic reconnection
        stopPolling();
        // Aggiorna viaggio se necessario
        updateCurrentTrip(null);

        // Close trip record on disconnection
        if (currentTrip != null && totalDistanceKm > 0.01) {
            currentTrip.endTrip(totalDistanceKm, calculateAverageSpeed(), calculateAverageFuelConsumption());
            tripLogManager.updateCurrentTrip(currentTrip);
            currentTrip = null;
        }

        isConnected = false;
        consecutiveBTErrors = 0;
        consecutiveNoData = 0;
        closeStreams();

        showStatus("Disconnected.");
        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);
        tvRpm.setText("RPM: --");
        tvSpeed.setText("Speed: --");
        tvTemp.setText("Temp: --");
        tvFuelRateInstantMaf.setText("Inst. km/L (MAF): --");
        tvFuelFlowMaf.setText("Inst. L/h (MAF): --");
        tvTotalKm.setText("Traveled km: --");
        tvAvgSpeed.setText("Avg speed: --");
        tvAvgFuelMaf.setText("Avg km/L (MAF): --");
        tvElmVersion.setText("ELM327: -- | Protocol: --");
    }

    private void closeStreams() {
        try { if (inputStream  != null) inputStream.close();  } catch (IOException ignored) {}
        try { if (outputStream != null) outputStream.close(); } catch (IOException ignored) {}
        try { if (bluetoothSocket != null) bluetoothSocket.close(); } catch (IOException ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Aggiorna viaggio se necessario
        updateCurrentTrip(null);
        disconnect();
    }

    // ─── DATA MODEL ──────────────────────────────────────────────────────

    static class OBDData {
        int rpm = -1;
        int speedKmh = -1;
        int tempCelsius = -41; // -41 = invalid (minimum valid is -40°C)
        float mafGps = -1f;   // g/s, -1 = not available
        int engineLoad = -1;  // %, 0-100, -1 = not available
        int iatCelsius = -41; // Intake Air Temperature (PID 0x0F), -41 = invalid
        int mapKpa = -1;      // Manifold Absolute Pressure (PID 0x0B), -1 = not available
        int baroKpa = -1;     // Barometric Pressure (PID 0x33), -1 = not available
        int throttlePosition = -1; // Throttle Position (PID 0x11), 0-100%, -1 = not available
        long sampleTimeMs = -1; // Timestamp of the sample, -1 = not set
    }

    // Nuova struttura per dati derivati/statistici e istantanei
    static class CalculatedData {
        float fuelMafL100 = -1f;
        float fuelFlowMafLh = -1f;
        float kmLMaf = -1f;
        double totalDistanceKm = 0.0;
        double totalFuelMafLiters = 0.0;
        double avgSpeed = 0.0;
        double avgKmLMaf = 0.0;
    }


    // ─── STATIC METHODS FOR DATA SHARING WITH SETTINGSACTIVITY ──────────

    public static String getLog() {
        return logBuffer.toString();
    }

    public static int getSelectedProtocol() {
        return selectedProtocol;
    }

    public static void setSelectedProtocol(int protocol) {
        selectedProtocol = protocol;
    }

    /**
     * Gets the official PID description according to SAE J1979 standard.
     * @param pid PID value in hexadecimal (e.g., 0x0C for RPM)
     * @return PID description or null if not available
     */
    public static String getPidDescription(int pid) {
        return PID_DESCRIPTIONS.get(pid);
    }

    /**
     * Returns the complete set of PIDs supported by the connected ECU.
     * @return Set of supported PIDs (hexadecimal values) or empty set if not connected
     */
    public static Set<Integer> getSupportedPids() {
        if (instance != null) {
            return new HashSet<>(instance.supportedPids);
        }
        return new HashSet<>();
    }

    private static MainActivity instance;
}
