package com.ffxx68.obdreader;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class TripHistoryActivity extends AppCompatActivity {

    private static final String KEY_LAST_EXPORT_TIME = "lastExportTripStartTime";

    private LinearLayout layoutTripList;
    private TextView tvNoTrips;
    private TripLogManager tripLogManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trip_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tripLogManager = new TripLogManager(this);
        layoutTripList = findViewById(R.id.layoutTripList);
        tvNoTrips = findViewById(R.id.tvNoTrips);

        ImageButton btnExport = findViewById(R.id.btnExportCsv);
        btnExport.setOnClickListener(v -> showExportDialog());

        loadTrips();
    }

    // ─── EXPORT ──────────────────────────────────────────────────────────

    private void showExportDialog() {
        List<TripLog> allTrips = tripLogManager.getAllTrips();
        if (allTrips.isEmpty()) {
            Toast.makeText(this, "Nessun viaggio da esportare", Toast.LENGTH_SHORT).show();
            return;
        }

        long lastExportTime = getPreferences(MODE_PRIVATE)
                .getLong(KEY_LAST_EXPORT_TIME, 0L);

        List<TripLog> newTrips = new java.util.ArrayList<>();
        for (TripLog t : allTrips) {
            // TESTING
            // if (t.getStartTime() > lastExportTime) {
                newTrips.add(t);
            //}
        }

        if (newTrips.isEmpty()) {
            Toast.makeText(this, "Nessun viaggio nuovo dall'ultimo export", Toast.LENGTH_SHORT).show();
            return;
        }

        exportAndSend(newTrips);
    }

    private void exportAndSend(List<TripLog> trips) {
        try {
            File csvFile = buildCsvFile(trips);
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", csvFile);

            String dateFrom = trips.get(trips.size() - 1).getStartTimeFormatted();
            String dateTo   = trips.get(0).getStartTimeFormatted();
            String subject  = trips.size() == 1
                    ? "OBD Reader – Viaggi " + dateFrom
                    : "OBD Reader – Viaggi " + dateFrom + " / " + dateTo;

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_SUBJECT, subject);
            intent.putExtra(Intent.EXTRA_TEXT, "In allegato lo storico dei viaggi in formato CSV.");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            long newestTripTime = trips.get(0).getStartTime();
            getPreferences(MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_EXPORT_TIME, newestTripTime)
                    .apply();

            startActivity(Intent.createChooser(intent, "Condividi via…"));
        } catch (IOException e) {
            Toast.makeText(this, "Errore durante la creazione del CSV: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private File buildCsvFile(List<TripLog> trips) throws IOException {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new java.util.Date());
        File csvFile = new File(getCacheDir(), "viaggi_" + timestamp + ".csv");

        try (FileWriter writer = new FileWriter(csvFile)) {
            // Header
            writer.write("Start,End,Duration,Distance_km,AvgSpeed_kmh,SpeedStdDev_kmh," +
                         "AvgKmL_MAF,AvgRPM,RPMStdDev\n");

            for (TripLog t : trips) {
                writer.write(String.format(Locale.US,
                        "%s,%s,%s,%.2f,%.1f,%.1f,%.2f,%.0f,%.0f\n",
                        t.getStartTimeFormatted(),
                        t.getEndTimeFormatted(),
                        t.getDuration(),
                        t.getTotalKm(),
                        t.getAvgSpeedKmh(),
                        t.getSpeedStdDev(),
                        t.getAvgKmLMaf(),
                        t.getAvgRpm(),
                        t.getRpmStdDev()));
            }
        }
        return csvFile;
    }

    private void loadTrips() {
        List<TripLog> trips = tripLogManager.getAllTrips();

        layoutTripList.removeAllViews();

        if (trips.isEmpty()) {
            tvNoTrips.setVisibility(View.VISIBLE);
            layoutTripList.setVisibility(View.GONE);
        } else {
            tvNoTrips.setVisibility(View.GONE);
            layoutTripList.setVisibility(View.VISIBLE);

            for (int i = 0; i < trips.size(); i++) {
                final TripLog trip = trips.get(i);
                final int position = i;
                View tripView = createTripView(trip, position);
                layoutTripList.addView(tripView);
            }
        }
    }

    private View createTripView(TripLog trip, int position) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 16, 24, 16);
        container.setBackgroundResource(R.drawable.trip_item_background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        container.setLayoutParams(params);

        // Trip start
        TextView tvStart = new TextView(this);
        tvStart.setText("Start: " + trip.getStartTimeFormatted());
        tvStart.setTextSize(16);
        tvStart.setTextColor(0xFFFFFFFF);
        tvStart.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(tvStart);

        // Trip end
        TextView tvEnd = new TextView(this);
        tvEnd.setText("End: " + trip.getEndTimeFormatted());
        tvEnd.setTextSize(14);
        tvEnd.setTextColor(0xFFD0D0D0);
        tvEnd.setPadding(0, 4, 0, 8);
        container.addView(tvEnd);

        // Duration
        TextView tvDuration = new TextView(this);
        tvDuration.setText("Duration: " + trip.getDuration());
        tvDuration.setTextSize(14);
        tvDuration.setTextColor(0xFF81C784);
        container.addView(tvDuration);

        // Distance traveled
        TextView tvKm = new TextView(this);
        tvKm.setText(String.format(Locale.US, "Distance: %.2f km", trip.getTotalKm()));
        tvKm.setTextSize(16);
        tvKm.setTextColor(0xFFFFFFFF);
        tvKm.setPadding(0, 8, 0, 0);
        container.addView(tvKm);

        // Average speed
        TextView tvSpeed = new TextView(this);
        tvSpeed.setText(String.format(Locale.US, "Average speed: %.1f km/h", trip.getAvgSpeedKmh()));
        tvSpeed.setTextSize(14);
        tvSpeed.setTextColor(0xFFD0D0D0);
        container.addView(tvSpeed);

        // Speed std dev
        TextView tvSpeedStd = new TextView(this);
        tvSpeedStd.setText(String.format(Locale.US, "Speed std dev: %.1f km/h", trip.getSpeedStdDev()));
        tvSpeedStd.setTextSize(14);
        tvSpeedStd.setTextColor(0xFFD0D0D0);
        container.addView(tvSpeedStd);

        // Average km/L MAF
        TextView tvKmLMaf = new TextView(this);
        tvKmLMaf.setText(String.format(Locale.US, "Avg km/L (MAF): %.2f", trip.getAvgKmLMaf()));
        tvKmLMaf.setTextSize(14);
        tvKmLMaf.setTextColor(0xFF81C784);
        container.addView(tvKmLMaf);

        // Avg RPM
        TextView tvAvgRpm = new TextView(this);
        tvAvgRpm.setText(String.format(Locale.US, "Avg RPM: %.0f", trip.getAvgRpm()));
        tvAvgRpm.setTextSize(14);
        tvAvgRpm.setTextColor(0xFFD0D0D0);
        container.addView(tvAvgRpm);

        // RPM std dev
        TextView tvRpmStd = new TextView(this);
        tvRpmStd.setText(String.format(Locale.US, "RPM std dev: %.0f", trip.getRpmStdDev()));
        tvRpmStd.setTextSize(14);
        tvRpmStd.setTextColor(0xFFD0D0D0);
        container.addView(tvRpmStd);


        // Long click to delete
        container.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete trip")
                .setMessage("Do you want to delete this trip?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    tripLogManager.deleteTrip(position);
                    loadTrips();
                })
                .setNegativeButton("Cancel", null)
                .show();
            return true;
        });

        return container;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
