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
        onLocationUpdate: (Location) -> Unit
    ) {
        // Runtime permission must be granted before calling this.
        if (callback != null) return

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
        ).addOnFailureListener {
            callback = null
        }
    }

    fun stopTracking() {
        callback?.let {
            client.removeLocationUpdates(it)
        }

        callback = null
    }
}