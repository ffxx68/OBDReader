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
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String PREFS_NAME = "OBDReaderPrefs";
    private static final String PREF_DEVICE_NAME = "selectedDeviceName";
    private static final String PREF_DEVICE_ADDRESS = "selectedDeviceAddress";
    private static final String PREF_PROTOCOL = "selectedProtocol";

    private RadioGroup rgProtocol;
    private TextView tvLog;
    private NestedScrollView scrollLog;
    private TextView tvSelectedDevice;
    private Button btnScanDevices;
    private Button btnTripHistory;
    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Abilita la freccia indietro nella action bar
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
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);
        tvSelectedDevice = findViewById(R.id.tvSelectedDevice);
        btnScanDevices = findViewById(R.id.btnScanDevices);
        btnTripHistory = findViewById(R.id.btnTripHistory);

        // Ascolta i cambiamenti del protocollo
        rgProtocol.setOnCheckedChangeListener((group, checkedId) -> saveProtocolSelection(checkedId));

        // Pulsante scansione dispositivi
        btnScanDevices.setOnClickListener(v -> checkPermissionsAndScan());

        // Pulsante storico viaggi
        btnTripHistory.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, TripHistoryActivity.class);
            startActivity(intent);
        });

        // Carica il log attuale da MainActivity
        String currentLog = MainActivity.getLog();
        if (!currentLog.isEmpty()) {
            tvLog.setText(currentLog);
            scrollLog.post(() -> scrollLog.fullScroll(NestedScrollView.FOCUS_DOWN));
        }
    }

    private void loadSettings() {
        // Carica il protocollo selezionato dalle SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int savedProtocol = prefs.getInt(PREF_PROTOCOL, R.id.rbAuto);
        rgProtocol.check(savedProtocol);

        // Aggiorna anche la variabile statica in MainActivity
        MainActivity.setSelectedProtocol(savedProtocol);
    }

    private void saveProtocolSelection(int checkedId) {
        // Salva nelle SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_PROTOCOL, checkedId);
        editor.apply();

        // Aggiorna anche la variabile statica in MainActivity
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
        // Aggiorna il log quando l'activity torna in primo piano
        String currentLog = MainActivity.getLog();
        if (!currentLog.isEmpty()) {
            tvLog.setText(currentLog);
            scrollLog.post(() -> scrollLog.fullScroll(NestedScrollView.FOCUS_DOWN));
        }
    }

    private void loadSelectedDevice() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String deviceName = prefs.getString(PREF_DEVICE_NAME, null);
        String deviceAddress = prefs.getString(PREF_DEVICE_ADDRESS, null);

        if (deviceName != null && deviceAddress != null) {
            tvSelectedDevice.setText(deviceName + "\n" + deviceAddress);
        } else {
            tvSelectedDevice.setText("Nessun dispositivo selezionato");
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
                        .setTitle("Permessi richiesti")
                        .setMessage("I permessi Bluetooth sono necessari per cercare dispositivi.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void scanAndShowDevices() {
        if (bluetoothAdapter == null) {
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth non disponibile")
                    .setMessage("Questo dispositivo non supporta il Bluetooth.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle("Bluetooth disattivato")
                    .setMessage("Attiva il Bluetooth nelle impostazioni del dispositivo.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        pairedDevices.clear();
        List<String> names = new ArrayList<>();

        for (BluetoothDevice d : bonded) {
            pairedDevices.add(d);
            String name = d.getName() != null ? d.getName() : "Sconosciuto";
            names.add(name + "\n" + d.getAddress());
        }

        if (pairedDevices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nessun dispositivo")
                    .setMessage("Nessun dispositivo Bluetooth accoppiato.\n\n"
                            + "Vai in Impostazioni → Bluetooth e accoppia l'ELM327 (PIN: 1234 o 6789)")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Seleziona dispositivo ELM327")
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    BluetoothDevice selected = pairedDevices.get(which);
                    String name = selected.getName() != null ? selected.getName() : "Sconosciuto";
                    saveSelectedDevice(name, selected.getAddress());
                })
                .setNegativeButton("Annulla", null)
                .show();
    }
}

