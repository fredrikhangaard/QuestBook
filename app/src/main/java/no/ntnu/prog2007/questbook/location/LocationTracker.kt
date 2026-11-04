package no.ntnu.prog2007.questbook.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper

import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Thin Fused Location client. Permission is checked by the screen, while route filtering
 * belongs to GpsRepository. This class only requests, forwards, and removes GPS updates.
 */
class LocationTracker(
    context: Context
) {

    private val client =
        LocationServices.getFusedLocationProviderClient(
            context.applicationContext
        )

    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun startTracking(
        onLocationUpdate: (Location) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        // Runtime permission must be granted before calling this.
        if (callback != null) return

        // Request high accuracy around every 3 seconds, with a 1-second minimum interval.
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            3000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val locationCallback = object : LocationCallback() {

            override fun onLocationResult(
                result: LocationResult
            ) {
                // A callback can contain several fixes; forward them all for ordering/filter checks.
                result.locations.forEach { location ->
                    onLocationUpdate(location)
                }
            }
        }

        callback = locationCallback

        client.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        ).addOnFailureListener { error ->
            // A late failure from a stopped request must not clear a newer callback.
            if (callback === locationCallback) {
                callback = null
                onError(error)
            }
        }
    }

    /** Removes the registered callback. The GPS engine also invalidates any callbacks already in flight. */
    fun stopTracking() {
        callback?.let {
            client.removeLocationUpdates(it)
        }

        callback = null
    }
}