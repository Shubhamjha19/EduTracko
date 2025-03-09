package com.example.safeedutrack

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.maps.model.LatLng

class GeofenceHelper(base: Context?) : ContextWrapper(base) {

    private var pendingIntent: PendingIntent? = null

    companion object {
        private const val TAG = "GeofenceHelper"
        private const val DEFAULT_LOITERING_DELAY = 5000 // 5 seconds
        private const val REQUEST_CODE = 0
    }

    /**
     * Builds a GeofencingRequest using the provided Geofence.
     */
    fun getGeofencingRequest(geofence: Geofence?): GeofencingRequest {
        requireNotNull(geofence) { "Geofence cannot be null." }
        Log.d(TAG, "Building GeofencingRequest for Geofence: ${geofence.requestId}")
        return GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .build()
    }

    /**
     * Creates and returns a Geofence instance.
     */
    fun getGeofence(
        ID: String,
        latLng: LatLng?,
        radius: Float,
        transitionTypes: Int
    ): Geofence {
        require(!ID.isBlank()) { "Geofence ID cannot be blank." }
        require(latLng != null) { "LatLng cannot be null." }
        require(radius > 0) { "Radius must be greater than 0." }

        Log.d(TAG, "Creating Geofence with ID: $ID, Location: $latLng, Radius: $radius")
        return Geofence.Builder()
            .setCircularRegion(latLng.latitude, latLng.longitude, radius)
            .setRequestId(ID)
            .setTransitionTypes(transitionTypes)
            .setLoiteringDelay(DEFAULT_LOITERING_DELAY)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }

    /**
     * Creates a PendingIntent for the Geofence transitions.
     */
    fun createPendingIntent(): PendingIntent {
        // Check if pendingIntent already exists and return it
        if (pendingIntent == null) {
            val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
            pendingIntent = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            Log.d(TAG, "Created new PendingIntent for Geofence transitions.")
        } else {
            Log.d(TAG, "Reusing existing PendingIntent.")
        }
        return pendingIntent!!
    }

    /**
     * Converts Geofencing API exceptions into user-readable error messages.
     */
    fun getErrorString(e: Exception): String {
        val errorMessage = if (e is ApiException) {
            when (e.statusCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> "Geofence not available."
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> "Too many geofences."
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> "Too many pending intents."
                else -> "Unknown geofence error: ${e.statusCode}"
            }
        } else {
            e.localizedMessage ?: "Unknown error"
        }
        Log.e(TAG, "Error: $errorMessage", e)
        return errorMessage
    }
}