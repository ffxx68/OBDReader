package com.ffxx68.obdreader;

import com.ffxx68.obdreader.MainActivity.CalculatedData;

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

    public TripLog() {
        this.startTime = System.currentTimeMillis();
    }

    public TripLog(long startTime, long endTime, double totalKm, double avgSpeedKmh,
                   double avgKmLMaf) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
    }

    public void endTrip(double totalKm, double avgSpeedKmh, double avgKmLMaf) {
        this.endTime = System.currentTimeMillis();
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
    }

    /**
     * Aggiorna i dati del viaggio usando la struttura CalculatedData
     */
    public void updateTrip(CalculatedData calc) {
        if (calc != null) {
            this.totalKm = calc.totalDistance;
            this.avgSpeedKmh = calc.avgSpeed;
            this.avgKmLMaf = calc.avgFuelRate;
        }
    }

    // Versione legacy per compatibilità
    public void updateTrip(double totalKm, double avgSpeedKmh, double avgKmLMaf) {
        this.totalKm = totalKm;
        this.avgSpeedKmh = avgSpeedKmh;
        this.avgKmLMaf = avgKmLMaf;
    }

    // Getters
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getTotalKm() { return totalKm; }
    public double getAvgSpeedKmh() { return avgSpeedKmh; }
    public double getAvgKmLMaf() { return avgKmLMaf; }

    // Setters
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setTotalKm(double totalKm) { this.totalKm = totalKm; }
    public void setAvgSpeedKmh(double avgSpeedKmh) { this.avgSpeedKmh = avgSpeedKmh; }
    public void setAvgKmLMaf(double avgKmLMaf) { this.avgKmLMaf = avgKmLMaf; }

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

