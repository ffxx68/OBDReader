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
 * Persistent trip history manager
 */
public class TripLogManager {
    private static final String PREFS_NAME = "TripLogs";
    private static final String KEY_TRIPS = "trips";
    private static final int MAX_TRIPS = 100; // Maximum number of saved trips

    private final SharedPreferences prefs;
    private final Gson gson;

    public TripLogManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }

    /**
     * Saves a new trip
     */
    public void saveTrip(TripLog trip) {
        List<TripLog> trips = getAllTrips();
        trips.add(0, trip); // Add to the beginning (most recent)

        // Keep only the last MAX_TRIPS
        if (trips.size() > MAX_TRIPS) {
            trips = trips.subList(0, MAX_TRIPS);
        }

        String json = gson.toJson(trips);
        prefs.edit().putString(KEY_TRIPS, json).apply();
    }

    /**
     * Updates the first trip (the current one) in the list
     */
    public void updateCurrentTrip(TripLog trip) {
        List<TripLog> trips = getAllTrips();
        if (!trips.isEmpty()) {
            trips.set(0, trip); // Update the first (most recent)
        } else {
            trips.add(trip); // If it doesn't exist, create it
        }

        String json = gson.toJson(trips);
        prefs.edit().putString(KEY_TRIPS, json).apply();
    }

    /**
     * Retrieves all saved trips
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
     * Deletes all trips
     */
    public void clearAllTrips() {
        prefs.edit().remove(KEY_TRIPS).apply();
    }

    /**
     * Deletes a specific trip
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
