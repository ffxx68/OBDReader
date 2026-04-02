package com.ffxx68.obdreader;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Gestore dello storico viaggi persistente
 */
public class TripLogManager {
    private static final String PREFS_NAME = "TripLogs";
    private static final String KEY_TRIPS = "trips";
    private static final int MAX_TRIPS = 100; // Massimo numero di viaggi salvati

    private final SharedPreferences prefs;
    private final Gson gson;

    public TripLogManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * Salva un nuovo viaggio
     */
    public void saveTrip(TripLog trip) {
        List<TripLog> trips = getAllTrips();
        trips.add(0, trip); // Aggiungi all'inizio (più recente)

        // Mantieni solo gli ultimi MAX_TRIPS
        if (trips.size() > MAX_TRIPS) {
            trips = trips.subList(0, MAX_TRIPS);
        }

        String json = gson.toJson(trips);
        prefs.edit().putString(KEY_TRIPS, json).apply();
    }

    /**
     * Aggiorna il primo viaggio (quello corrente) nella lista
     */
    public void updateCurrentTrip(TripLog trip) {
        List<TripLog> trips = getAllTrips();
        if (!trips.isEmpty()) {
            trips.set(0, trip); // Aggiorna il primo (il più recente)
        } else {
            trips.add(trip); // Se non esiste, crealo
        }

        String json = gson.toJson(trips);
        prefs.edit().putString(KEY_TRIPS, json).apply();
    }

    /**
     * Recupera tutti i viaggi salvati
     */
    public List<TripLog> getAllTrips() {
        String json = prefs.getString(KEY_TRIPS, null);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        Type listType = new TypeToken<ArrayList<TripLog>>(){}.getType();
        List<TripLog> trips = gson.fromJson(json, listType);
        return trips != null ? trips : new ArrayList<>();
    }

    /**
     * Cancella tutti i viaggi
     */
    public void clearAllTrips() {
        prefs.edit().remove(KEY_TRIPS).apply();
    }

    /**
     * Elimina un viaggio specifico
     */
    public void deleteTrip(int position) {
        List<TripLog> trips = getAllTrips();
        if (position >= 0 && position < trips.size()) {
            trips.remove(position);
            String json = gson.toJson(trips);
            prefs.edit().putString(KEY_TRIPS, json).apply();
        }
    }
}

