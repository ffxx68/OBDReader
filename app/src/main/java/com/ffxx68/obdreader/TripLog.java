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
    private int fuelType = 0;
    private long[] rpmBuckets   = new long[4];
    private long[] speedBuckets = new long[4];

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
            this.fuelType = calc.fuelType;
            this.rpmBuckets   = calc.rpmBuckets != null ? calc.rpmBuckets.clone() : new long[4];
            this.speedBuckets = calc.speedBuckets != null ? calc.speedBuckets.clone() : new long[4];
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
    public int getFuelType() { return fuelType; }
    public long[] getRpmBuckets()   { return rpmBuckets   != null ? rpmBuckets   : new long[4]; }
    public long[] getSpeedBuckets() { return speedBuckets != null ? speedBuckets : new long[4]; }

    // Setters
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setTotalKm(double totalKm) { this.totalKm = totalKm; }
    public void setAvgSpeedKmh(double avgSpeedKmh) { this.avgSpeedKmh = avgSpeedKmh; }
    public void setAvgKmLMaf(double avgKmLMaf) { this.avgKmLMaf = avgKmLMaf; }
    public void setFuelType(int fuelType) { this.fuelType = fuelType; }
    public void setRpmBuckets(long[] rpmBuckets) { this.rpmBuckets = rpmBuckets != null ? rpmBuckets.clone() : new long[4]; }
    public void setSpeedBuckets(long[] speedBuckets) { this.speedBuckets = speedBuckets != null ? speedBuckets.clone() : new long[4]; }

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
        long end = (endTime > 0) ? endTime : System.currentTimeMillis();
        long durationMs = end - startTime;
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        String suffix = (endTime <= 0) ? " *" : "";
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d h %02d min%s", hours, minutes, suffix);
        } else {
            return String.format(Locale.getDefault(), "%d min%s", minutes, suffix);
        }
    }
}

