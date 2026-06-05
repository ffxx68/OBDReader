package com.ffxx68.obdreader;

import com.ffxx68.obdreader.MainActivity.CalculatedData;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * Classe che rappresenta un singolo viaggio registrato
 */
public class TripLog {
    private long segmentStartTime;
    private long segmentEndTime;
    private long startTime;
    private long endTime;
    // Timestamp dell'ultima lettura valida dai sensori (aggiornato SOLO quando arrivano dati)
    private long lastUpdateTime;
    private double segmentKm;
    private double totalKm;
    private double segmentAvgSpeedKmh;
    private double averageSpeedKmh;
    private double segmentAvgKmLMaf;
    private double averageKmLMaf;
    private int fuelType = 0;
    private long[] rpmBuckets   = new long[4];
    private long[] speedBuckets = new long[4];
    private String sessionId;
    private int segmentIndex;

    public TripLog() {
        this.segmentStartTime = System.currentTimeMillis();
        this.startTime = this.segmentStartTime;
        this.lastUpdateTime = this.startTime; // inizializza lastUpdate allo start per evitare durate crescenti prima della prima lettura
        this.sessionId = UUID.randomUUID().toString();
        this.segmentIndex = 0;
    }

    public TripLog(long segmentStartTime, long segmentEndTime, double segmentKm, double avgSpeedKmh,
                   double segmentAvgKmLMaf) {
        this.segmentStartTime = segmentStartTime;
        this.segmentEndTime = segmentEndTime;
        this.segmentKm = segmentKm;
        this.segmentAvgSpeedKmh = avgSpeedKmh;
        this.segmentAvgKmLMaf = segmentAvgKmLMaf;
    }

    public void endSegment(double segmentKm, double segmentAvgSpeedKmh, double segmentAvgKmLMaf) {
        this.segmentEndTime = System.currentTimeMillis();
        this.segmentKm = segmentKm;
        this.segmentAvgSpeedKmh = segmentAvgSpeedKmh;
        this.segmentAvgKmLMaf = segmentAvgKmLMaf;
    }

    public void endTrip(double segmentKm, double segmentAvgSpeedKmh, double segmentAvgKmLMaf,
                        double totalKm, double avgSpeedKmh, double avgKmLMaf) {
        endSegment(segmentKm, segmentAvgSpeedKmh, segmentAvgKmLMaf);
        this.endTime = System.currentTimeMillis();
        this.lastUpdateTime = this.endTime;
        this.totalKm = totalKm;
        this.averageSpeedKmh = avgSpeedKmh;
        this.averageKmLMaf = avgKmLMaf;
    }

    /**
     * Aggiorna i dati del segmento di viaggio, dalla struttura CalculatedData
     */
    public void updateTrip(CalculatedData calc) {
        if (calc != null) {
            this.segmentKm = calc.segmentDistance;
            this.segmentAvgSpeedKmh = calc.segmentAvgSpeed;
            this.segmentAvgKmLMaf = calc.segmentFuelRate;
            this.averageSpeedKmh = calc.averageSpeed;
            this.averageKmLMaf = calc.averageFuelRate;
            this.fuelType = calc.fuelType;
            this.rpmBuckets   = calc.rpmBuckets != null ? calc.rpmBuckets.clone() : new long[4];
            this.speedBuckets = calc.speedBuckets != null ? calc.speedBuckets.clone() : new long[4];
        }
    }

    // Setter/getter per lastUpdateTime
    public long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }

    // Versione legacy per compatibilità
    public void updateTrip(double totalKm, double avgSpeedKmh, double avgKmLMaf) {
        this.segmentKm = totalKm;
        this.segmentAvgSpeedKmh = avgSpeedKmh;
        this.segmentAvgKmLMaf = avgKmLMaf;
    }

    // Getters
    public long getSegmentStartTime() { return segmentStartTime; }
    public long getSegmentEndTime() { return segmentEndTime; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getSegmentKm() { return segmentKm; }
    public double getSegmentAvgSpeedKmh() { return segmentAvgSpeedKmh; }
    public double getSegmentAvgKmLMaf() { return segmentAvgKmLMaf; }
    public int getFuelType() { return fuelType; }
    public long[] getRpmBuckets()   { return rpmBuckets   != null ? rpmBuckets   : new long[4]; }
    public long[] getSpeedBuckets() { return speedBuckets != null ? speedBuckets : new long[4]; }

    // Setters
    public void setSegmentStartTime(long segmentStartTime) { this.segmentStartTime = segmentStartTime; }
    public void setSegmentEndTime(long segmentEndTime) { this.segmentEndTime = segmentEndTime; }
    public void setSegmentKm(double segmentKm) { this.segmentKm = segmentKm; }
    public void setSegmentAvgSpeedKmh(double segmentAvgSpeedKmh) { this.segmentAvgSpeedKmh = segmentAvgSpeedKmh; }
    public void setSegmentAvgKmLMaf(double segmentAvgKmLMaf) { this.segmentAvgKmLMaf = segmentAvgKmLMaf; }
    public void setFuelType(int fuelType) { this.fuelType = fuelType; }
    public void setRpmBuckets(long[] rpmBuckets) { this.rpmBuckets = rpmBuckets != null ? rpmBuckets.clone() : new long[4]; }
    public void setSpeedBuckets(long[] speedBuckets) { this.speedBuckets = speedBuckets != null ? speedBuckets.clone() : new long[4]; }
    public String getSessionId() { return sessionId != null ? sessionId : "legacy"; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public int getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(int segmentIndex) { this.segmentIndex = segmentIndex; }

    /**
     * Crea un nuovo segmento nella stessa sessione di connessione.
     */
    /**
     * Crea un nuovo segmento mantenendo il tempo di inizio viaggio originale.
     */
    public static TripLog startSegment(String sessionId, int segmentIndex, Long tripStartTime) {
        TripLog seg = new TripLog();
        seg.sessionId = sessionId;
        seg.segmentIndex = segmentIndex;
        if (tripStartTime != null && tripStartTime > 0) {
            seg.startTime = tripStartTime;
            // Do NOT set lastUpdateTime to the overall trip start: lastUpdateTime must refer
            // to the timestamp of the latest VALID data sample. For a new segment created
            // now, initialize lastUpdateTime to the new segment start time to avoid
            // negative or inflated segment durations.
            seg.lastUpdateTime = seg.segmentStartTime;
        }
        return seg;
    }

    // Formattazione date
    public String getSegmentStartTimeFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(segmentStartTime));
    }

    public String getSegmentEndTimeFormatted() {
        if (segmentEndTime > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(segmentEndTime));
        }
        return "In corso...";
    }

    public String getSegmentDuration() {
        long end;
        if (segmentEndTime > 0) {
            end = segmentEndTime;
        } else if (lastUpdateTime > 0) {
            end = lastUpdateTime; // usa l'ultima lettura valida quando il segmento è ancora in corso
        } else {
            end = System.currentTimeMillis();
        }
        long durationMs = end - segmentStartTime;
        long hours = durationMs / 3600000;
        long minutes = (durationMs % 3600000) / 60000;
        String suffix = (segmentEndTime <= 0) ? " *" : "";
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d h %02d min%s", hours, minutes, suffix);
        } else {
            return String.format(Locale.getDefault(), "%d min%s", minutes, suffix);
        }
    }

    public String getTripDuration() {
        long end;
        if (endTime > 0) {
            end = endTime;
        } else if (lastUpdateTime > 0) {
            end = lastUpdateTime; // usa l'ultima lettura valida quando il viaggio è ancora in corso
        } else {
            end = System.currentTimeMillis();
        }
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

