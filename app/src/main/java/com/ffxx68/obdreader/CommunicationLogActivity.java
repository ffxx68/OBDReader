package com.ffxx68.obdreader;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

public class CommunicationLogActivity extends AppCompatActivity {

    private TextView tvLog;
    private NestedScrollView scrollLog;
    private static final int MAX_LOG_LINES = 200;
    private static StringBuilder logBuffer = new StringBuilder();

    public static void logCriticalError(String context, Exception e) {
        StringBuilder msg = new StringBuilder();
        msg.append("[ERRORE] [").append(new java.util.Date()).append("] ")
           .append(context).append(": ").append(e.toString()).append("\n");
        for (StackTraceElement ste : e.getStackTrace()) {
            msg.append("    at ").append(ste.toString()).append("\n");
        }
        logBuffer.append(msg);
        // Mantieni il buffer entro MAX_LOG_LINES
        String[] lines = logBuffer.toString().split("\n", -1);
        if (lines.length > MAX_LOG_LINES) {
            int excess = lines.length - MAX_LOG_LINES;
            StringBuilder trimmed = new StringBuilder();
            for (int i = excess; i < lines.length; i++) {
                trimmed.append(lines[i]).append("\n");
            }
            logBuffer = trimmed;
        }
    }

    public static void logMessage(String msg) {
        logBuffer.append(msg).append("\n");
        String[] lines = logBuffer.toString().split("\n", -1);
        if (lines.length > MAX_LOG_LINES) {
            int excess = lines.length - MAX_LOG_LINES;
            StringBuilder trimmed = new StringBuilder();
            for (int i = excess; i < lines.length; i++) {
                trimmed.append(lines[i]).append("\n");
            }
            logBuffer = trimmed;
        }
    }

    public static String getLog() {
        return logBuffer.toString();
    }

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
        String currentLog = getLog();
        if (!currentLog.isEmpty()) {
            tvLog.setText(currentLog);
            scrollLog.post(() -> scrollLog.fullScroll(NestedScrollView.FOCUS_DOWN));
        } else {
            tvLog.setText("No log available.\nConnect to the ECU to see communication.");
        }
    }
}
