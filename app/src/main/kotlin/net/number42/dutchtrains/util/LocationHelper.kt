package net.number42.dutchtrains.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

open class LocationHelper(private val context: Context) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Throws(SecurityException::class)
    open suspend fun getCurrentLocation(): Result<Location> = suspendCancellableCoroutine { cont ->
        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
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
}
