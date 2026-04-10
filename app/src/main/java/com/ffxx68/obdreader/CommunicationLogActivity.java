package com.ffxx68.obdreader;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

public class CommunicationLogActivity extends AppCompatActivity {

    private TextView tvLog;
    private NestedScrollView scrollLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication_log);

        // Enable back arrow in the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        // Load the current log from MainActivity
        loadLog();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update the log when the activity comes to foreground
        loadLog();
    }

    private void loadLog() {
        String currentLog = MainActivity.getLog();
        if (!currentLog.isEmpty()) {
            tvLog.setText(currentLog);
            scrollLog.post(() -> scrollLog.fullScroll(NestedScrollView.FOCUS_DOWN));
        } else {
            tvLog.setText("No log available.\nConnect to the ECU to see communication.");
        }
    }
}
