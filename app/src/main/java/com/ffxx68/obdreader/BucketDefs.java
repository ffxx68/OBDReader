package com.ffxx68.obdreader;

import java.util.Locale;

/**
 * Definizioni centralizzate degli intervalli bucket per velocità e RPM.
 * Usare questa classe come unica fonte di verità per soglie ed etichette.
 */
public final class BucketDefs {

    private BucketDefs() {}

    // ─── VELOCITÀ ────────────────────────────────────────────────────────
    private static final int[] SPEED_THRESHOLDS = {40, 90, 110};
    public  static final String[] SPEED_LABELS  = {"<40", "40-90", "90-110", ">110"};

    public static int speedBucket(int speed) {
        for (int i = 0; i < SPEED_THRESHOLDS.length; i++) {
            if (speed < SPEED_THRESHOLDS[i]) return i;
        }
        return SPEED_THRESHOLDS.length;
    }

    // ─── GIRI (DIESEL) ───────────────────────────────────────────────────
    private static final int[] RPM_DIESEL_THRESHOLDS = {1000, 2000, 3000};
    public  static final String[] RPM_DIESEL_LABELS  = {"<1000", "1-2k", "2-3k", ">3k"};

    // ─── GIRI (BENZINA) ──────────────────────────────────────────────────
    private static final int[] RPM_PETROL_THRESHOLDS = {1500, 3500, 4500};
    public  static final String[] RPM_PETROL_LABELS  = {"<1500", "1.5-3.5k", "3.5-4.5k", ">4.5k"};

    public static int rpmBucket(int rpm, int fuelType) {
        int[] thresholds = (fuelType == MainActivity.FUEL_PETROL)
                ? RPM_PETROL_THRESHOLDS : RPM_DIESEL_THRESHOLDS;
        for (int i = 0; i < thresholds.length; i++) {
            if (rpm < thresholds[i]) return i;
        }
        return thresholds.length;
    }

    // ─── FORMATTAZIONE UI ────────────────────────────────────────────────

    /** Restituisce la stringa di distribuzione velocità, o "Velocità: n/d". */
    public static String formatSpeed(long[] buckets) {
        long total = 0;
        for (long b : buckets) total += b;
        if (total == 0) return "Velocità: n/d";
        return String.format(Locale.US,
                "Vel: %s=%.0f%% | %s=%.0f%% | %s=%.0f%% | %s=%.0f%%",
                SPEED_LABELS[0], 100.0 * buckets[0] / total,
                SPEED_LABELS[1], 100.0 * buckets[1] / total,
                SPEED_LABELS[2], 100.0 * buckets[2] / total,
                SPEED_LABELS[3], 100.0 * buckets[3] / total);
    }

    /** Restituisce la stringa di distribuzione RPM (con prefisso D/B), o "Giri: n/d". */
    public static String formatRpm(long[] buckets, int fuelType) {
        long total = 0;
        for (long b : buckets) total += b;
        if (total == 0) return "Giri: n/d";
        boolean isDiesel = (fuelType != MainActivity.FUEL_PETROL);
        String[] labels = isDiesel ? RPM_DIESEL_LABELS : RPM_PETROL_LABELS;
        String prefix   = isDiesel ? "RPM(D)" : "RPM(B)";
        return String.format(Locale.US,
                "%s: %s=%.0f%% | %s=%.0f%% | %s=%.0f%% | %s=%.0f%%",
                prefix,
                labels[0], 100.0 * buckets[0] / total,
                labels[1], 100.0 * buckets[1] / total,
                labels[2], 100.0 * buckets[2] / total,
                labels[3], 100.0 * buckets[3] / total);
    }

    // ─── CSV ─────────────────────────────────────────────────────────────
    /** Colonne CSV per i bucket di velocità (4 valori). */
    public static String speedCsvHeader() {
        return "Speed_" + SPEED_LABELS[0] + "pct,"
             + "Speed_" + SPEED_LABELS[1] + "pct,"
             + "Speed_" + SPEED_LABELS[2] + "pct,"
             + "Speed_" + SPEED_LABELS[3] + "pct";
    }

    /** Colonne CSV per i bucket RPM (4 valori), distinte per tipo carburante. */
    public static String rpmCsvHeader(int fuelType) {
        String[] labels = (fuelType == MainActivity.FUEL_PETROL) ? RPM_PETROL_LABELS : RPM_DIESEL_LABELS;
        String tag = (fuelType == MainActivity.FUEL_PETROL) ? "B" : "D";
        return "RPM" + tag + "_" + labels[0] + "pct,"
             + "RPM" + tag + "_" + labels[1] + "pct,"
             + "RPM" + tag + "_" + labels[2] + "pct,"
             + "RPM" + tag + "_" + labels[3] + "pct";
    }
}

