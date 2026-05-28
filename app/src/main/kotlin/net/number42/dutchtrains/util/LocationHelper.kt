package net.number42.dutchtrains.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

open class LocationHelper(private val context: Context) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Throws(SecurityException::class)
    open suspend fun getCurrentLocation(): Result<Location> {
        // Last-known location returns immediately and is accurate enough for station detection.
        val lastKnown = try {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
                .mapNotNull { locationManager.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
        } catch (_: SecurityException) { null }

        if (lastKnown != null) return Result.success(lastKnown)

        // No cached fix — request a fresh one, but give up after 10 seconds.
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> {
                        cont.resume(Result.failure(Exception("No location provider available")))
                        return@suspendCancellableCoroutine
                    }
                }

                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }

                try {
                    LocationManagerCompat.getCurrentLocation(
                        locationManager,
                        provider,
                        signal,
                        Executors.newSingleThreadExecutor(),
                    ) { location: Location? ->
                        if (location != null) cont.resume(Result.success(location))
                        else cont.resume(Result.failure(Exception("Location unavailable — ensure GPS is enabled")))
                    }
                } catch (e: SecurityException) {
                    cont.resume(Result.failure(Exception("Location permission not granted")))
                }
            }
        } ?: Result.failure(Exception("Location timed out"))
    }
}
