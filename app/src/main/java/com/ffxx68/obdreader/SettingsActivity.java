package com.ffxx68.obdreader;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;

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
    private TextView tvSupportedPids;
    private NestedScrollView scrollPids;
    private TextView tvSelectedDevice;
    private Button btnScanDevices;
    private Button btnTripHistory;
    private Button btnCommLog;
    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();

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
        tvSupportedPids = findViewById(R.id.tvSupportedPids);
        scrollPids = findViewById(R.id.scrollPids);
        tvSelectedDevice = findViewById(R.id.tvSelectedDevice);
        btnScanDevices = findViewById(R.id.btnScanDevices);
        btnTripHistory = findViewById(R.id.btnTripHistory);
        btnCommLog = findViewById(R.id.btnCommLog);

        // Listen for protocol changes
        rgProtocol.setOnCheckedChangeListener((group, checkedId) -> saveProtocolSelection(checkedId));

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

        // Load PIDs supported by the ECU
        loadSupportedPids();
    }

    private void loadSettings() {
        // Load selected protocol from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedProtocol = prefs.getInt(PREF_PROTOCOL, R.id.rbAuto);
        rgProtocol.check(savedProtocol);

        // Also update the static variable in MainActivity
        MainActivity.setSelectedProtocol(savedProtocol);
    }

    private void saveProtocolSelection(int checkedId) {
        // Save to SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_PROTOCOL, checkedId);
        editor.apply();

        // Also update the static variable in MainActivity
        MainActivity.setSelectedProtocol(checkedId);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update supported PIDs when activity comes to foreground
        loadSupportedPids();
    }

    private void loadSupportedPids() {
        Set<Integer> supportedPids = MainActivity.getSupportedPids();

        if (supportedPids.isEmpty()) {
            tvSupportedPids.setText("Connect to the ECU to see supported PIDs.");
            return;
        }

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
