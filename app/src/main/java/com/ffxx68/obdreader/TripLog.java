package com.ffxx68.obdreader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Classe che rappresenta un singolo viaggio registrato
 */
public class TripLog {
    private long startTime;
    private long endTime;
    private double totalKm;
    private double avgSpeedKmh;
    private double avgKmLMaf;
    private double avgKmLSd;

    public TripLog() {
        this.startTime = System.currentTimeMillis();
    }

    public TripLog(long startTime, long endTime, double totalKm, double avgSpeedKmh,
                   double avgKmLMaf, double avgKmLSd) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
        this.avgKmLSd = avgKmLSd;
    }

    public void endTrip(double totalKm, double avgSpeedKmh, double avgKmLMaf, double avgKmLSd) {
        this.endTime = System.currentTimeMillis();
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
        this.avgKmLSd = avgKmLSd;
    }

    public void updateTrip(double totalKm, double avgSpeedKmh, double avgKmLMaf, double avgKmLSd) {
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
        this.avgKmLSd = avgKmLSd;
    }

    // Getters
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getTotalKm() { return totalKm; }
    public double getAvgSpeedKmh() { return avgSpeedKmh; }
    public double getAvgKmLMaf() { return avgKmLMaf; }
    public double getAvgKmLSd() { return avgKmLSd; }

    // Setters
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setTotalKm(double totalKm) { this.totalKm = totalKm; }
    public void setAvgSpeedKmh(double avgSpeedKmh) { this.avgSpeedKmh = avgSpeedKmh; }
    public void setAvgKmLMaf(double avgKmLMaf) { this.avgKmLMaf = avgKmLMaf; }
    public void setAvgKmLSd(double avgKmLSd) { this.avgKmLSd = avgKmLSd; }

    // Formattazione date
    public String getStartTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(startTime));
    }

    public String getEndTimeFormatted() {
        if (endTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(endTime));
        }
        return "In corso...";
    }

    public String getDuration() {
        if (endTime <= 0) return "In corso...";
        long durationMs = endTime - startTime;
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        long seconds = (durationMs % 60000) / 1000;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
    }
}

