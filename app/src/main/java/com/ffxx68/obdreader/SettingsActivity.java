package com.ffxx68.obdreader;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.content.BroadcastReceiver;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "OBDReaderPrefs";
    private static final String PREF_DEVICE_NAME = "selectedDeviceName";
    private static final String PREF_DEVICE_ADDRESS = "selectedDeviceAddress";
    private static final String PREF_PROTOCOL = "selectedProtocol";

    private RadioGroup rgProtocol;
    private RadioGroup rgFuelType;
    private TextView tvSupportedPids;
    private NestedScrollView scrollPids;
    private TextView tvSelectedDevice;
    private Button btnScanDevices;
    private Button btnTripHistory;
    private Button btnCommLog;
    private EditText etKmLCorrection;
    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();

    private final BroadcastReceiver pidsUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if ("ACTION_PIDS_UPDATED".equals(intent.getAction())) {
                    loadSupportedPids();
                }
            } catch (Exception e) {
                CommunicationLogActivity.logCriticalError("SettingsActivity.BroadcastReceiver", e);
            }
        }
    };
    // Attempts to request PID refresh from MainActivity when the list is empty
    private int pidRefreshAttempts = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Enable back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initViews();
        loadSettings();
        loadSelectedDevice();
    }

    private void initViews() {
        rgProtocol = findViewById(R.id.rgProtocol);
        rgFuelType = findViewById(R.id.rgFuelType);
        tvSupportedPids = findViewById(R.id.tvSupportedPids);
        scrollPids = findViewById(R.id.scrollPids);
        tvSelectedDevice = findViewById(R.id.tvSelectedDevice);
        btnScanDevices = findViewById(R.id.btnScanDevices);
        btnTripHistory = findViewById(R.id.btnTripHistory);
        btnCommLog = findViewById(R.id.btnCommLog);
        etKmLCorrection = findViewById(R.id.etKmLCorrection);

        // Listen for protocol changes
        rgProtocol.setOnCheckedChangeListener((group, checkedId) -> saveProtocolSelection(checkedId));

        // Listen for fuel type changes
        rgFuelType.setOnCheckedChangeListener((group, checkedId) -> saveFuelTypeSelection(checkedId));

        // Scan devices button
        btnScanDevices.setOnClickListener(v -> checkPermissionsAndScan());

        // Trip history button
        btnTripHistory.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, TripHistoryActivity.class);
            startActivity(intent);
        });

        // Communication log button
        btnCommLog.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, CommunicationLogActivity.class);
            startActivity(intent);
        });

        etKmLCorrection.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                saveKmLCorrectionFactor();
            }
        });

        // Load PIDs supported by the ECU
        loadSupportedPids();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        int savedProtocol = prefs.getInt(PREF_PROTOCOL, R.id.rbAuto);
        rgProtocol.check(savedProtocol);
        MainActivity.setSelectedProtocol(savedProtocol);

        int savedFuelType = prefs.getInt(MainActivity.PREF_FUEL_TYPE, MainActivity.FUEL_DIESEL);
        rgFuelType.check(savedFuelType == MainActivity.FUEL_PETROL ? R.id.rbPetrol : R.id.rbDiesel);

        float correction = sanitizeKmLCorrectionFactor(
                prefs.getFloat(MainActivity.PREF_KM_L_CORRECTION, MainActivity.DEFAULT_KM_L_CORRECTION));
        etKmLCorrection.setText(formatKmLCorrection(correction));
    }

    private void saveProtocolSelection(int checkedId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(PREF_PROTOCOL, checkedId).apply();
        MainActivity.setSelectedProtocol(checkedId);
    }

    private void saveFuelTypeSelection(int checkedId) {
        int fuelType = (checkedId == R.id.rbPetrol) ? MainActivity.FUEL_PETROL : MainActivity.FUEL_DIESEL;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putInt(MainActivity.PREF_FUEL_TYPE, fuelType).apply();
        MainActivity instance = MainActivity.getInstance();
        if (instance != null) instance.setFuelType(fuelType);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Aggiorna i PID anche se la Activity torna in foreground
        loadSupportedPids();
        // Registra il receiver per aggiornamento automatico
        LocalBroadcastManager.getInstance(this).registerReceiver(pidsUpdatedReceiver, new IntentFilter("ACTION_PIDS_UPDATED"));
    }

    @Override
    protected void onPause() {
        saveKmLCorrectionFactor();
        super.onPause();
        // Deregistra il receiver per evitare memory leak
        LocalBroadcastManager.getInstance(this).unregisterReceiver(pidsUpdatedReceiver);
    }

    private void saveKmLCorrectionFactor() {
        String raw = etKmLCorrection.getText() != null ? etKmLCorrection.getText().toString() : "";
        float factor;
        try {
            factor = Float.parseFloat(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            factor = MainActivity.DEFAULT_KM_L_CORRECTION;
        }
        factor = sanitizeKmLCorrectionFactor(factor);

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putFloat(MainActivity.PREF_KM_L_CORRECTION, factor)
                .apply();

        etKmLCorrection.setText(formatKmLCorrection(factor));
        MainActivity instance = MainActivity.getInstance();
        if (instance != null) {
            instance.setKmLCorrectionFactor(factor);
        }
    }

    private float sanitizeKmLCorrectionFactor(float factor) {
        return factor > 0f ? factor : MainActivity.DEFAULT_KM_L_CORRECTION;
    }

    private String formatKmLCorrection(float factor) {
        if (Math.abs(factor - Math.round(factor)) < 0.0001f) {
            return String.valueOf((int) Math.round(factor));
        }
        return String.valueOf(factor);
    }

    private void loadSupportedPids() {
        Set<Integer> supportedPids = MainActivity.getSupportedPids();

        if (supportedPids.isEmpty()) {
            // Try a short refresh cycle before showing the final message
            if (pidRefreshAttempts == 0) {
                tvSupportedPids.setText("Reading supported PIDs...");
            } else {
                tvSupportedPids.setText("Reading supported PIDs... (retry " + pidRefreshAttempts + ")");
            }
            if (pidRefreshAttempts < 3) {
                pidRefreshAttempts++;
                MainActivity.requestPidsRefresh();
                // retry after a short delay
                tvSupportedPids.postDelayed(this::loadSupportedPids, 1500);
                return;
            }

            // after retries, show final guidance
            tvSupportedPids.setText("Connect to the ECU to see supported PIDs.");
            // Request one last time in case the app missed the broadcast
            MainActivity.requestPidsRefresh();
            return;
        }

        // Reset attempts when successful
        pidRefreshAttempts = 0;

        // Sort PIDs for display
        List<Integer> sortedPids = new ArrayList<>(supportedPids);
        Collections.sort(sortedPids);

        StringBuilder pidList = new StringBuilder();
        pidList.append("PIDs supported by ECU (").append(supportedPids.size()).append("):\n\n");

        for (int pid : sortedPids) {
            String description = MainActivity.getPidDescription(pid);
            if (description != null) {
                pidList.append(String.format("0x%02X - %s\n", pid, description));
            } else {
                pidList.append(String.format("0x%02X - (unknown)\n", pid));
            }
        }

        tvSupportedPids.setText(pidList.toString());
    }

    private void loadSelectedDevice() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceName = prefs.getString(PREF_DEVICE_NAME, null);
        String deviceAddress = prefs.getString(PREF_DEVICE_ADDRESS, null);

        if (deviceName != null && deviceAddress != null) {
            tvSelectedDevice.setText(deviceName + "\n" + deviceAddress);
        } else {
            tvSelectedDevice.setText("No device selected");
        }
    }

    private void saveSelectedDevice(String name, String address) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_DEVICE_NAME, name);
        editor.putString(PREF_DEVICE_ADDRESS, address);
        editor.apply();

        tvSelectedDevice.setText(name + "\n" + address);
    }

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
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                scanAndShowDevices();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Permissions required")
                        .setMessage("Bluetooth permissions are required to search for devices.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void scanAndShowDevices() {
        if (bluetoothAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth not available")
                    .setMessage("This device does not support Bluetooth.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth disabled")
                    .setMessage("Enable Bluetooth in device settings.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        pairedDevices.clear();
        List<String> names = new ArrayList<>();

        for (BluetoothDevice d : bonded) {
            pairedDevices.add(d);
            String name = d.getName() != null ? d.getName() : "Unknown";
            names.add(name + "\n" + d.getAddress());
        }

        if (pairedDevices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No devices")
                    .setMessage("No paired Bluetooth devices.\n\n"
                            + "Go to Settings → Bluetooth and pair the ELM327 (PIN: 1234 or 6789)")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select ELM327 device")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    BluetoothDevice selected = pairedDevices.get(which);
                    String name = selected.getName() != null ? selected.getName() : "Unknown";
                    saveSelectedDevice(name, selected.getAddress());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
