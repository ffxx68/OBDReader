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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // Standard UUID for Serial Port Profile (SPP) - used by ELM327
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int READ_INTERVAL_MS = 500; // polling interval
    private static final int INITIAL_POLL_DELAY_MS = 2500; // delay before first poll after init
    private static final int MAX_LOG_LINES = 200;     // ~100 command/response exchanges
    private static final String PREFS_NAME = "OBDReaderPrefs";
    private static final String PREF_DEVICE_NAME = "selectedDeviceName";
    private static final String PREF_DEVICE_ADDRESS = "selectedDeviceAddress";
    private static final String PREF_PROTOCOL = "selectedProtocol";
    static final String PREF_FUEL_TYPE = "fuelType";
    static final int FUEL_DIESEL  = 0;
    static final int FUEL_PETROL  = 1;

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
    private int noDataGracePeriod = 0; // Tentativi iniziali post-connessione da non mostrare come "Engine off"
    private static final int INITIAL_GRACE_PERIOD = 5; // Numero di NO DATA iniziali da ignorare silenziosamente
    private static final int MAX_BT_ERRORS_BEFORE_RECONNECT = 3;  // Max retry connessione BT prima di dare errore
    private static final int MAX_NO_DATA_BEFORE_RETRY = 3; // Max retry prima del re-init ECU
    private static final int MAX_NO_DATA_BEFORE_CLOSE_TRIP = 150; // Max retry prima di chiudere il viaggio

    // Handler per aggiornamenti UI dal thread BT
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    private Runnable segmentRunnable;

    // Sessione di connessione corrente
    private String currentSessionId = null;
    private int currentSegmentIndex = 0;
    private static final long SEGMENT_INTERVAL_MS = 15 * 60 * 1000L; // 15 minuti

    // UI Elements
    private TextView tvStatus;
    private TextView tvRpm;
    private TextView tvInstantSpeed;
    private TextView tvTemp;
    private TextView tvTripDuration;
    private TextView tvInstantFuelRate;
    private TextView tvInstantFuelFlow;
    private TextView tvTotalDistance;
    private TextView tvTotLiters;
    private TextView tvAvgSpeed;
    private TextView tvAvgFuelRate;
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

    // Accumulatori per statistiche velocità e RPM
    private long[] rpmBuckets   = new long[4]; // bucket counts for RPM
    private long[] speedBuckets = new long[4]; // bucket counts for speed

    // Gestione log viaggi
    private TripLogManager tripLogManager;
    private TripLog currentTrip;

    // Lista dei PID supportati dalla modalità 01
    // Supporta fino a PID 0xE0 (224) tramite query multiple (0100, 0120, 0140, ecc.)
    private Set<Integer> supportedPids = new HashSet<>();

    // Tipo carburante selezionato dall'utente (FUEL_DIESEL / FUEL_PETROL)
    private int fuelType = FUEL_DIESEL;

    // Flag per abilitare la modalità mock solo su emulatore
    private boolean useMockData = false;

    // --- MOCK ERROR SIMULATION ---
    private static int mockReadCount = 0;

    /**
     * Rileva se l'app è in esecuzione su un emulatore Android.
     */
    public static boolean isEmulator() {
        String fingerprint = android.os.Build.FINGERPRINT;
        String model = android.os.Build.MODEL;
        String manufacturer = android.os.Build.MANUFACTURER;
        String brand = android.os.Build.BRAND;
        String device = android.os.Build.DEVICE;
        String product = android.os.Build.PRODUCT;
        String hardware = android.os.Build.HARDWARE;

        return (fingerprint != null && (fingerprint.startsWith("generic") || fingerprint.contains("google/sdk_gphone") || fingerprint.startsWith("unknown")))
                || (model != null && (model.contains("google_sdk") || model.contains("Emulator") || model.contains("Android SDK built for x86") || model.contains("sdk_gphone")))
                || (manufacturer != null && (manufacturer.contains("Genymotion") || manufacturer.equalsIgnoreCase("unknown") || manufacturer.equalsIgnoreCase("Google")))
                || (brand != null && (brand.startsWith("generic") || brand.equalsIgnoreCase("android") || brand.equalsIgnoreCase("google")))
                || (device != null && (device.startsWith("generic") || device.startsWith("emulator") || device.contains("emu")))
                || (product != null && (product.equals("google_sdk") || product.contains("sdk") || product.contains("emulator") || product.contains("simulator") || product.contains("sdk_gphone")))
                || (hardware != null && (hardware.contains("goldfish") || hardware.contains("ranchu") || hardware.contains("qcom")));

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        initViews(); // Inizializza subito le view
        useMockData = isEmulator();
        if (useMockData) {
            showStatus("Modalità MOCK attiva (emulatore rilevato). Dati OBD simulati.");
        }
        tripLogManager = new TripLogManager(this);
        loadSavedSettings();
        initBluetooth();
        setupListeners();

    }

    // ─── INIZIALIZZAZIONE ────────────────────────────────────────────────────

    private void loadSavedSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedProtocol = prefs.getInt(PREF_PROTOCOL, R.id.rbAuto);
        fuelType = prefs.getInt(PREF_FUEL_TYPE, FUEL_DIESEL);
    }

    private void initViews() {

        tvStatus    = findViewById(R.id.tvStatus);
        tvRpm       = findViewById(R.id.tvRpm);
        tvInstantSpeed     = findViewById(R.id.tvSpeed);
        tvTripDuration = findViewById(R.id.tvTripDuration);
        tvInstantFuelRate = findViewById(R.id.tvFuelRateInstantMaf);
        tvInstantFuelFlow = findViewById(R.id.tvFuelFlow);
        tvTotalDistance   = findViewById(R.id.tvTotalKm);
        tvTotLiters   = findViewById(R.id.tvTotLiters);
        tvAvgSpeed  = findViewById(R.id.tvAvgSpeed);
        tvAvgFuelRate = findViewById(R.id.tvAvgFuelMaf);
        tvElmVersion = findViewById(R.id.tvElmVersion);
        btnConnect      = findViewById(R.id.btnConnect);
        btnDisconnect   = findViewById(R.id.btnDisconnect);
        progressBar     = findViewById(R.id.progressBar);
        btnSettings     = findViewById(R.id.btnSettings);

        btnDisconnect.setEnabled(false);
    }

    private void initBluetooth() {
        if (useMockData) {
            showStatus("Modalità MOCK attiva. Tocca 'Connetti' per simulare la ECU");
            btnConnect.setEnabled(true);
            return;
        }
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

        // Aggiorna la modalità mock anche qui
        useMockData = isEmulator();

        // Ricarica preferenze che potrebbero essere cambiate in SettingsActivity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        fuelType = prefs.getInt(PREF_FUEL_TYPE, FUEL_DIESEL);
        if (useMockData) {
            showStatus("Modalità MOCK attiva. Tocca 'Connetti' per simulare la ECU");
            btnConnect.setEnabled(true);
            return;
        }
        if (!isConnected && bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
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

    // Mock: connessione ECU
    private void connectToSavedDeviceMock() {
        showStatus("[MOCK] Connessione simulata alla ECU");
        showProgress(false);
        isConnected = true;
        btnConnect.setEnabled(false);
        btnDisconnect.setEnabled(true);
        supportedPids.clear();
        supportedPids.addAll(MOCK_PIDS);
        // Avvia polling mock
        startPolling();
        if (currentTrip == null) {
            currentSessionId = java.util.UUID.randomUUID().toString();
            currentSegmentIndex = 0;
            currentTrip = TripLog.startSegment(currentSessionId, currentSegmentIndex);
            tripLogManager.saveTrip(currentTrip);
            startSegmentTimer();
        }
    }

    // Mock: dati OBD
    private OBDData fetchOBDDataMock() {
        OBDData data = new OBDData();
        data.rpm = 900 + (int)(Math.random()*200);
        data.instantSpeed = 50 + (int)(Math.random()*10);
        data.tempCelsius = 85 + (int)(Math.random()*5);
        data.engineLoad = 30 + (int)(Math.random()*10);
        data.mafGps = 12.5f + (float)(Math.random()*2);
        data.iatCelsius = 25 + (int)(Math.random()*3);
        data.mapKpa = 110 + (int)(Math.random()*5);
        data.baroKpa = 100 + (int)(Math.random()*2);
        data.throttlePosition = 40 + (int)(Math.random()*10);
        data.fuelRateEcuGps = -1f;
        return data;
    }

    // Override connectToSavedDevice
    private void connectToSavedDevice() {
        if (useMockData) {
            connectToSavedDeviceMock();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceAddress = prefs.getString(PREF_DEVICE_ADDRESS, null);
        String deviceName = prefs.getString(PREF_DEVICE_NAME, null);
        if (deviceAddress == null) {
            showStatus("Nessun dispositivo selezionato. Vai nelle Impostazioni.");
            return;
        }
        shouldStayConnected = true;
        consecutiveBTErrors = 0;
        consecutiveNoData = 0;
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
        try {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device);
        } catch (IllegalArgumentException e) {
            showStatus("Indirizzo dispositivo non valido: " + deviceAddress);
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
                noDataGracePeriod = INITIAL_GRACE_PERIOD; // Reset grace period post-connessione
                mainHandler.post(() -> {
                    showStatus("Connesso alla ECU");
                    showProgress(false);
                    btnConnect.setEnabled(false);
                    btnDisconnect.setEnabled(true);
                    // Avvia automaticamente l'aggiornamento continuo
                    startPolling();
                    // Crea il record del viaggio alla connessione SOLO se non esiste già (prima connessione)
                    if (currentTrip == null) {
                        currentSessionId = java.util.UUID.randomUUID().toString();
                        currentSegmentIndex = 0;
                        currentTrip = TripLog.startSegment(currentSessionId, currentSegmentIndex);
                        tripLogManager.saveTrip(currentTrip);
                        startSegmentTimer();
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

    /** Eccezione specifica per errori di comunicazione con la ECU (distinta da errori BT). */
    static class EcuConnectionException extends IOException {
        EcuConnectionException(String message) { super(message); }
    }

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
                // Invia broadcast per aggiornare la lista PID in SettingsActivity
                Intent intent = new Intent("ACTION_PIDS_UPDATED");
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
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
        if (outputStream == null || inputStream == null) {
            NullPointerException npe = new NullPointerException("outputStream o inputStream null in sendCommand");
            CommunicationLogActivity.logCriticalError("sendCommand", npe);
            throw npe;
        }
        try {
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
                CommunicationLogActivity.logMessage(finalLogLine);
            });

            return result;
        } catch (IOException | NullPointerException e) {
            CommunicationLogActivity.logCriticalError("sendCommand", e);
            throw e;
        }
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
            rpmBuckets   = new long[4];
            speedBuckets = new long[4];
        }
        lastUpdateTimeMs = System.currentTimeMillis();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;

                new Thread(() -> {
                    try {
                        if (useMockData) {
                            mockReadCount++;
                            if (mockReadCount == 6) {
                                throw new RuntimeException("Simulated critical error after 5 mock reads");
                            }
                        }
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
                        if (noDataGracePeriod > 0) noDataGracePeriod--;

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
                                if (noDataGracePeriod > 0) {
                                    showStatus("Lettura ECU in corso...");
                                } else {
                                    showStatus("Engine off? Attempt " + consecutiveNoData + "/" + MAX_NO_DATA_BEFORE_CLOSE_TRIP);
                                }
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
                    } catch (Exception e) {
                        CommunicationLogActivity.logCriticalError("Polling thread", e);
                        mainHandler.post(() -> {
                            showStatus("Errore imprevisto: " + e.getMessage());
                            if (isPolling) mainHandler.postDelayed(pollingRunnable, READ_INTERVAL_MS);
                        });
                    }
                }).start();
            }
        };
        mainHandler.postDelayed(pollingRunnable, INITIAL_POLL_DELAY_MS);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingRunnable != null) mainHandler.removeCallbacks(pollingRunnable);
        stopSegmentTimer();
        // Aggiorna viaggio se necessario
        updateCurrentTrip(null);
    }

    private void startSegmentTimer() {
        stopSegmentTimer();
        segmentRunnable = new Runnable() {
            @Override
            public void run() {
                rotateSegment();
                mainHandler.postDelayed(this, SEGMENT_INTERVAL_MS);
            }
        };
        mainHandler.postDelayed(segmentRunnable, SEGMENT_INTERVAL_MS);
    }

    private void stopSegmentTimer() {
        if (segmentRunnable != null) {
            mainHandler.removeCallbacks(segmentRunnable);
            segmentRunnable = null;
        }
    }

    /**
     * Chiude il segmento corrente e ne apre uno nuovo con lo stesso sessionId.
     */
    private void rotateSegment() {
        if (currentTrip == null || currentSessionId == null) return;

        // Chiudi il segmento corrente
        currentTrip.endTrip(totalDistanceKm, calculateAverageSpeed(), calculateAverageFuelConsumption());
        tripLogManager.updateCurrentTrip(currentTrip);

        // Reset statistiche per il nuovo segmento
        totalDistanceKm = 0.0;
        totalFuelMafLiters = 0.0;
        rpmBuckets   = new long[4];
        speedBuckets = new long[4];
        lastUpdateTimeMs = System.currentTimeMillis();

        // Nuovo segmento nella stessa sessione
        currentSegmentIndex++;
        currentTrip = TripLog.startSegment(currentSessionId, currentSegmentIndex);
        tripLogManager.saveTrip(currentTrip);
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
        if (useMockData) return fetchOBDDataMock();

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
        data.instantSpeed = parseSpeed(speedRaw);

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

        // Engine Fuel Rate — PID 0x5E — Formula: ((A*256)+B)*0.05 → L/h (direct ECU measurement)
        String fuelRateRaw = sendCommand("015E");
        data.fuelRateEcuGps = parseFuelRateEcu(fuelRateRaw);

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

    private float parseFuelRateEcu(String raw) {
        // PID 0x5E - Engine Fuel Rate: ((A*256)+B)*0.05 → L/h
        List<Integer> b = extractBytes(raw, "415E");
        if (b.size() < 2) return -1f;
        return ((b.get(0) * 256) + b.get(1)) * 0.05f;
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


    // ─── AFR CALCULATION ─────────────────────────────────────────────────
    /**
     * Diesel AFR curve (piecewise linear, load-based).
     *
     * 1) Modern common-rail diesel runs with large excess air at idle/low load.
     * As load increases, AFR drops toward stoichiometric (~14.5:1).
     *
     * | Load  | AFR    | Condition                        |
     * |-------|--------|----------------------------------|
     * |  0 %  | 80 : 1 | Idle – almost no fuel injected   |
     * | 10 %  | 70 : 1 | Very light load                  |
     * | 25 %  | 40 : 1 | Light cruise                     |
     * | 60 %  | 20 : 1 | Normal / highway                 |
     * |100 %  | 14 : 1 | Full load                        |
     *
     * FUNCTION REPLACED - WHY NOT ENGINE_LOAD (PID 0x04)?
     * ─────────────────────────────────────────────────────────────────────
     * On most diesel ECUs, calculated engine load is derived from the MAF
     * sensor reading:
     *
     *   LOAD = (MAF_actual / MAF_max_theoretical) × 100
     *
     * Using LOAD to select AFR, and then dividing MAF by that AFR to obtain
     * fuel flow, creates a circular dependency: the correction factor is
     * computed from the very value it is meant to correct. No independent
     * information is added, and any MAF bias at idle (where MAF_max is a
     * poor reference) propagates directly into the fuel estimate.
     *
     */
     /*
     private float calculateDieselAFR(int engineLoad, int rpm) {
        if (engineLoad < 0) return 27.0f;
        int load = Math.max(0, Math.min(100, engineLoad));

        // Some PD ECUs report inflated load at idle (often 20–35%)
        // because load PID is MAF-ratio based, not torque-based.
        // Use RPM to detect true idle and override load-based AFR.
        if (rpm > 0 && rpm < 1000) {
            // True idle: interpolate between 900 rpm floor and load=0 AFR
            // PD engines at idle: AFR realistically 50–65:1
            return 60.0f;
        }

        int[]   loadPts = {  0,  10,  25,  60, 100 };
        float[] afrPts  = { 80f, 70f, 40f, 20f, 14f };

        for (int i = 0; i < loadPts.length - 1; i++) {
            if (load <= loadPts[i + 1]) {
                float t = (float)(load - loadPts[i]) / (loadPts[i + 1] - loadPts[i]);
                return afrPts[i] + t * (afrPts[i + 1] - afrPts[i]);
            }
        }
        return afrPts[afrPts.length - 1];
    }
    */
    /**
     * Estimates the Air-Fuel Ratio (AFR) for a diesel engine using RPM and
     * boost pressure as independent load proxies.
     *
     * WHY RPM + BOOST PRESSURE?
     * ─────────────────────────────────────────────────────────────────────
     * Both signals are physically independent of the MAF sensor:
     *
     *  • RPM is measured by the crankshaft position sensor and reliably
     *    identifies true idle (< 900 rpm on a warmed-up 1.9 TDI PD),
     *    where ECU-reported load is known to be inflated (often 20–35 %
     *    even with negligible fueling).
     *
     *  • Boost pressure (MAP − barometric pressure) is measured by a
     *    dedicated pressure sensor and reflects actual engine breathing
     *    and turbocharger output — a genuine proxy for torque demand,
     *    unaffected by MAF calibration drift.
     *
     * PUMP-INJECTOR (PUMPE-DÜSE) NOTE:
     * ─────────────────────────────────────────────────────────────────────
     * PD injectors (e.g. VW/Audi AGR, ALH, ASZ, BKD ~1999–2006) are cam-
     * driven and cannot meter arbitrarily small fuel quantities. As a result,
     * the minimum injection dose at idle is larger than on common-rail
     * systems, and real-world AFR at idle is considerably lower (~50–65:1)
     * than the >100:1 values seen on modern common-rail engines.
     * The idle AFR constant below (60.0) reflects this constraint.
     *
     * AFR CURVE (boost-pressure based):
     * ─────────────────────────────────────────────────────────────────────
     *  Boost (relative)  |  AFR   | Condition
     *  ──────────────────|────────|──────────────────────────────────────
     *   RPM < 1000       | 60 : 1 | True idle (RPM override)
     *    0 %             | 55 : 1 | No boost, light throttle
     *   10 %             | 45 : 1 | Very light load
     *   35 %             | 28 : 1 | Part load / urban cruise
     *   70 %             | 18 : 1 | Highway / moderate boost
     *  100 %             | 14 : 1 | Full load
     *
     * @param rpm      Engine speed in RPM (from PID 0x0C)
     * @param mapKpa   Intake manifold absolute pressure in kPa (PID 0x0B)
     * @param baroKpa  Barometric pressure in kPa (PID 0x33); if unavailable,
     *                 pass 101 as a standard sea-level fallback
     * @return         Estimated AFR (dimensionless mass ratio, air / fuel)
     */
    private float calculateDieselAFR(int rpm, int mapKpa, int baroKpa) {
        // Boost pressure = MAP - baro (negative = vacuum, positive = boost)
        float boostKpa = mapKpa - baroKpa;

        // Normalize boost to a 0–1 load proxy
        // A typical 1.9 TDI PD peaks around 180 kPa absolute = ~80 kPa boost
        float boostLoad = Math.max(0f, Math.min(1f, boostKpa / 80f));

        // Idle detection via RPM (independent of MAF/LOAD)
        if (rpm < 1000) {
            return 60.0f; // true idle, PD engine
        }

        // Piecewise on boost load instead of ECU load
        float[] boostPts = { 0.00f, 0.10f, 0.35f, 0.70f, 1.00f };
        float[] afrPts   = { 55.0f, 45.0f, 28.0f, 18.0f, 14.0f };

        for (int i = 0; i < boostPts.length - 1; i++) {
            if (boostLoad <= boostPts[i + 1]) {
                float t = (boostLoad - boostPts[i])
                        / (boostPts[i + 1] - boostPts[i]);
                return afrPts[i] + t * (afrPts[i + 1] - afrPts[i]);
            }
        }
        return afrPts[afrPts.length - 1];
    }

    /**
     * Petrol (gasoline) AFR curve.
     *
     * Petrol engines are always near stoichiometric (14.7:1) when the closed-loop
     * lambda control is active. The MAF already encodes actual air mass, so the
     * AFR is nearly constant; small corrections for idle and WOT are applied.
     *
     * | Load  | AFR    | Condition                              |
     * |-------|--------|----------------------------------------|
     * |  0 %  | 14.7:1 | Idle – lambda control active           |
     * | 20 %  | 14.7:1 | Part throttle – lambda control active  |
     * | 80 %  | 14.7:1 | Cruise – lambda control active         |
     * | 90 %  | 13.0:1 | WOT enrichment begins                  |
     * |100 %  | 12.5:1 | Full enrichment for max power          |
     */
    private float calculatePetrolAFR(int engineLoad) {
        if (engineLoad < 0) return 14.7f;
        int load = Math.max(0, Math.min(100, engineLoad));

        // Closed-loop region (0–80 %): stoichiometric
        if (load <= 80) return 14.7f;

        // WOT enrichment (80–100 %): linearly richer
        float t = (float)(load - 80) / 20f;
        return 14.7f + t * (12.5f - 14.7f); // → 12.5 at 100 %
    }

    /**
     * Selects diesel or petrol AFR branch and applies an IAT density correction.
     *
     * IAT correction rationale: the MAF sensor measures mass flow, so it already
     * accounts for air density changes. However, on older MAP-based ECUs, or when
     * the MAF reading drifts, a small correction (<±5 %) for inlet temperature can
     * improve accuracy. The correction is intentionally conservative.
     *
     * @param engineLoad       Calculated engine load 0–100 %, -1 if unavailable
     * @param rpm              Engine RPM, -1 if unavailable (reserved for future use)
     * @param iatCelsius       Intake air temperature °C, -41 if unavailable
     * @param throttlePosition Throttle position 0–100 %, -1 if unavailable
     *                         (ignored on diesel; used on petrol only as WOT hint)
     * @return Effective AFR to use in fuel-flow calculation
     */
    private float calculateEnhancedAFR(int engineLoad, int rpm,
                                        int iatCelsius,
                                       int mapKpa, int baroKpa, int throttlePosition) {
        float afr;

        try {
            if (fuelType == FUEL_PETROL) {
                // ── PETROL ──────────────────────────────────────────────────
                afr = calculatePetrolAFR(engineLoad);

                // At WOT (throttle > 85 %) use throttle position as an additional
                // confirmation of enrichment rather than relying on load alone,
                // because some ECUs report load < 80 % even under full throttle.
                if (throttlePosition >= 85 && afr > 13.0f) {
                    afr = 13.0f; // enforce enrichment floor
                }

            } else {
                // ── DIESEL ──────────────────────────────────────────────────
                afr = calculateDieselAFR(rpm, mapKpa, baroKpa);

                // PID 0x11 (throttle) controls EGR/swirl on diesel, not fuel quantity.
                // No correction applied here.
            }

            // Minor IAT correction (applies to both types).
            // Reference: 20 °C = no correction. Valid range: -20 °C to +60 °C.
            // Effect: ±3 % max — avoids over-correction when MAF already compensates.
            if (iatCelsius > -41) {
                float iatFactor = 1.0f + (20 - iatCelsius) / 800f; // ≈ +3 % at -20 °C, -3 % at +60 °C
                afr *= iatFactor;
            }

            return afr;

        } catch ( Exception e) {
            CommunicationLogActivity.logCriticalError("calculateEnhancedAFR", e);
            throw e;
        }
    }

    /**
     * Calculates fuel flow rate in L/h from MAF sensor reading.
     *
     * Formula:  L/h = (MAF_g/s / AFR) / density_g/L × 3600
     *   Diesel density ≈ 840 g/L
     *   Petrol density ≈ 750 g/L
     */
    private float calcFuelFlowMaf(float mafGps, int engineLoad, int rpm, int iatCelsius,
                                   int mapKpa, int baroKpa, int throttlePosition) {
        if (mafGps < 0) return -1f;

        if (baroKpa == -1) baroKpa = 101; // Fallback to standard sea-level pressure if baro is unavailable
        float afr = calculateEnhancedAFR(engineLoad, rpm, iatCelsius, mapKpa, baroKpa ,throttlePosition);
        float density = (fuelType == FUEL_PETROL) ? 750f : 840f;

        float litersPerSec = mafGps / afr / density;
        return litersPerSec * 3600f;
    }


    // ─── UI HELPERS ──────────────────────────────────────────────────────

    private void updateUI(OBDData data, CalculatedData calc) {
        // RPM
        tvRpm.setText(data.rpm >= 0
                ? data.rpm + " rpm"
                : "N/A");

        // Speed (Km/h)
        tvInstantSpeed.setText(data.instantSpeed >= 0
                ? data.instantSpeed + " km/h"
                : "N/A");

        // Display instantaneous km/L
        tvInstantFuelRate.setText(calc.instantFuelRate > 0
                ? String.format(java.util.Locale.US, "%.2f", calc.instantFuelRate)
                : (data.instantSpeed == 0 ? "stopped" : "N/A"));

        // Display fuel flow rate in L/h (liters per hour)
        tvInstantFuelFlow.setText(calc.InstantFuelFlow > 0
                ? String.format(java.util.Locale.US, "%.2f", calc.InstantFuelFlow)
                : "N/A");

        // Display traveled km
        tvTotalDistance.setText(String.format(java.util.Locale.US, "%.2f", calc.totalDistance));

        // Display total fuel consumed in liters (Maf-based)
        tvTotLiters.setText(String.format(java.util.Locale.US, "%.2f", calc.totalFuelMafLiters));

        // Average speed
        if (calc.avgSpeed > 0) {
            tvAvgSpeed.setText(String.format(java.util.Locale.US, "%.1f km/h", calc.avgSpeed));
        } else {
            tvAvgSpeed.setText("--");
        }

        // Average km/L
        if (calc.avgFuelRate > 0) {
            tvAvgFuelRate.setText(String.format(java.util.Locale.US, "%.2f", calc.avgFuelRate));
        } else {
            tvAvgFuelRate.setText("--");
        }

        // Trip duration
        if (currentTrip != null) {
            tvTripDuration.setText(currentTrip.getDuration());
        }
    }

    // ─── CALCULATIONS ─────────────────────────────────────────────────────

    private CalculatedData calculateDerivedData(OBDData data) {
        CalculatedData calc = new CalculatedData();

        // Fuel flow: prefer PID 0x5E (ECU direct measurement), fallback to MAF
        if (data.fuelRateEcuGps >= 0) {
            calc.InstantFuelFlow = data.fuelRateEcuGps; // already in L/h
        } else {
            calc.InstantFuelFlow = calcFuelFlowMaf(data.mafGps, data.engineLoad, data.rpm,
                    data.iatCelsius, data.mapKpa, data.baroKpa, data.throttlePosition);
        }

        // L/100km and km/L
        if (calc.InstantFuelFlow > 0 && data.instantSpeed > 0) {
            calc.fuelRateL100 = (calc.InstantFuelFlow / data.instantSpeed) * 100f;
        } else {
            calc.fuelRateL100 = -1f;
        }
        calc.instantFuelRate = (calc.fuelRateL100 > 0) ? (100.0f / calc.fuelRateL100) : -1f;

        // Trip statistics (distance, fuel, average speed/consumption)
        if (data.instantSpeed > 0) {
            long currentTimeMs = System.currentTimeMillis();
            if (lastUpdateTimeMs > 0) {
                double elapsedHours = (currentTimeMs - lastUpdateTimeMs) / 3600000.0; // ms to hours
                double distanceKm = data.instantSpeed * elapsedHours;
                totalDistanceKm += distanceKm;
                if (calc.InstantFuelFlow > 0) {
                    totalFuelMafLiters += calc.InstantFuelFlow * elapsedHours;
                }
            }
            lastUpdateTimeMs = System.currentTimeMillis();
        }

        // Accumulate speed samples (only when moving)
        if (data.instantSpeed >= 0) {
            speedBuckets[BucketDefs.speedBucket(data.instantSpeed)]++;
        }
        // Accumulate RPM samples
        if (data.rpm >= 0) {
            rpmBuckets[BucketDefs.rpmBucket(data.rpm, fuelType)]++;
        }

        calc.totalDistance = totalDistanceKm;
        calc.totalFuelMafLiters = totalFuelMafLiters;
        calc.avgSpeed = calculateAverageSpeed();
        calc.avgFuelRate = calculateAverageFuelConsumption();

        calc.fuelType    = fuelType;
        calc.rpmBuckets  = rpmBuckets.clone();
        calc.speedBuckets = speedBuckets.clone();

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
        }
        currentTrip = null;
        currentSessionId = null;
        currentSegmentIndex = 0;

        isConnected = false;
        consecutiveBTErrors = 0;
        consecutiveNoData = 0;
        closeStreams();
        btnConnect.setEnabled(true);
        btnDisconnect.setEnabled(false);

        showStatus("Disconnected.");
        tvElmVersion.setText("ELM327: -- | Protocol: --");
        tvRpm.setText("--");
        tvInstantSpeed.setText("--");
        tvInstantFuelRate.setText("--");
        tvInstantFuelFlow.setText("--");
        tvTotalDistance.setText("--");
        tvTotLiters.setText("--");
        tvAvgSpeed.setText("--");
        tvAvgFuelRate.setText("--");

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
        int instantSpeed = -1;
        int tempCelsius = -41; // -41 = invalid (minimum valid is -40°C)
        float mafGps = -1f;   // g/s, -1 = not available
        int engineLoad = -1;  // %, 0-100, -1 = not available
        int iatCelsius = -41; // Intake Air Temperature (PID 0x0F), -41 = invalid
        int mapKpa = -1;      // Manifold Absolute Pressure (PID 0x0B), -1 = not available
        int baroKpa = -1;     // Barometric Pressure (PID 0x33), -1 = not available
        int throttlePosition = -1; // Throttle Position (PID 0x11), 0-100%, -1 = not available
        float fuelRateEcuGps = -1f; // Engine Fuel Rate (PID 0x5E), g/s, -1 = not available
        long sampleTimeMs = -1; // Timestamp of the sample, -1 = not set
    }

    // Nuova struttura per dati derivati/statistici e istantanei
    static class CalculatedData {
        float fuelRateL100 = -1f;
        float InstantFuelFlow = -1f;
        float instantFuelRate = -1f;
        double totalDistance = 0.0;
        double totalFuelMafLiters = 0.0;
        double avgSpeed = 0.0;
        double avgFuelRate = 0.0;
        int fuelType = FUEL_DIESEL;
        long[] rpmBuckets   = new long[4];
        long[] speedBuckets = new long[4];
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
        MainActivity inst = getInstance();
        if (inst != null && inst.useMockData) {
            return new HashSet<>(MOCK_PIDS);
        }
        if (inst != null) {
            return new HashSet<>(inst.supportedPids);
        }
        return new HashSet<>();
    }

    private static MainActivity instance;

    public static MainActivity getInstance() { return instance; }

    public void setFuelType(int type) { fuelType = type; }

    // PID mockati per test
    private static final Set<Integer> MOCK_PIDS = new HashSet<>(Arrays.asList(
        0x0C, // RPM
        0x0D, // Velocità
        0x05, // Temp liquido
        0x04, // Carico motore
        0x10, // MAF
        0x0F, // IAT
        0x0B, // MAP
        0x33, // Baro
        0x11  // TPS
    ));



}
