package no.ntnu.prog2007.questbook.location

import android.content.Context

/** Application-context adapter shared by the test screen and quest tracking. */
class AndroidGpsSource(context: Context) : GpsSource {
    // Application context prevents a retained GPS session from holding an Activity alive.
    private val tracker = LocationTracker(context.applicationContext)
    override fun start(onPoint: (RoutePoint, Boolean) -> Unit) = start(onPoint, {})
    override fun start(onPoint: (RoutePoint, Boolean) -> Unit, onError: (Throwable) -> Unit) {
        tracker.startTracking(onLocationUpdate = { location ->
            // hasAccuracy distinguishes a measured accuracy from Android's default zero value.
            onPoint(RoutePoint(location.latitude, location.longitude, location.time, location.accuracy), location.hasAccuracy())
        }, onError = onError)
    }
    override fun stop() = tracker.stopTracking()
}
