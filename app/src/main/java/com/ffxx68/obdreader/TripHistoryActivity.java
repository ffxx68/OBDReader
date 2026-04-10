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

        // Average km/L MAF
        TextView tvKmLMaf = new TextView(this);
        tvKmLMaf.setText(String.format(Locale.US, "Avg km/L (MAF): %.2f", trip.getAvgKmLMaf()));
        tvKmLMaf.setTextSize(14);
        tvKmLMaf.setTextColor(0xFF81C784);
        container.addView(tvKmLMaf);


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
