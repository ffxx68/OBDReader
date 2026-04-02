package com.ffxx68.obdreader;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

public class TripHistoryActivity extends AppCompatActivity {

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

        loadTrips();
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
        container.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        container.setLayoutParams(params);

        // Inizio viaggio
        TextView tvStart = new TextView(this);
        tvStart.setText("Inizio: " + trip.getStartTimeFormatted());
        tvStart.setTextSize(16);
        tvStart.setTextColor(0xFFFFFFFF);
        tvStart.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(tvStart);

        // Fine viaggio
        TextView tvEnd = new TextView(this);
        tvEnd.setText("Fine: " + trip.getEndTimeFormatted());
        tvEnd.setTextSize(14);
        tvEnd.setTextColor(0xFFB0BEC5);
        tvEnd.setPadding(0, 4, 0, 8);
        container.addView(tvEnd);

        // Durata
        TextView tvDuration = new TextView(this);
        tvDuration.setText("Durata: " + trip.getDuration());
        tvDuration.setTextSize(14);
        tvDuration.setTextColor(0xFF4CAF50);
        container.addView(tvDuration);

        // Km percorsi
        TextView tvKm = new TextView(this);
        tvKm.setText(String.format(Locale.US, "Km percorsi: %.2f km", trip.getTotalKm()));
        tvKm.setTextSize(16);
        tvKm.setTextColor(0xFFFFFFFF);
        tvKm.setPadding(0, 8, 0, 0);
        container.addView(tvKm);

        // Velocità media
        TextView tvSpeed = new TextView(this);
        tvSpeed.setText(String.format(Locale.US, "Velocità media: %.1f km/h", trip.getAvgSpeedKmh()));
        tvSpeed.setTextSize(14);
        tvSpeed.setTextColor(0xFFB0BEC5);
        container.addView(tvSpeed);

        // km/L medio MAF
        TextView tvKmLMaf = new TextView(this);
        tvKmLMaf.setText(String.format(Locale.US, "km/L medio (MAF): %.2f", trip.getAvgKmLMaf()));
        tvKmLMaf.setTextSize(14);
        tvKmLMaf.setTextColor(0xFF4CAF50);
        container.addView(tvKmLMaf);

        // km/L medio SD
        TextView tvKmLSd = new TextView(this);
        tvKmLSd.setText(String.format(Locale.US, "km/L medio (SD): %.2f", trip.getAvgKmLSd()));
        tvKmLSd.setTextSize(14);
        tvKmLSd.setTextColor(0xFF4CAF50);
        container.addView(tvKmLSd);

        // Click per eliminare
        container.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Elimina viaggio")
                .setMessage("Vuoi eliminare questo viaggio?")
                .setPositiveButton("Elimina", (dialog, which) -> {
                    tripLogManager.deleteTrip(position);
                    loadTrips();
                })
                .setNegativeButton("Annulla", null)
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

