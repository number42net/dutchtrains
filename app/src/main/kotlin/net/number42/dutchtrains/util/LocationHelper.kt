package net.number42.dutchtrains.util

import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @Throws(SecurityException::class)
    suspend fun getCurrentLocation(): Result<Location> = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        try {
            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) cont.resume(Result.success(location))
                    else cont.resume(Result.failure(Exception("Location unavailable — ensure GPS is enabled")))
                }
                .addOnFailureListener { e ->
                    cont.resume(Result.failure(e))
                }
        } catch (e: SecurityException) {
            cont.resume(Result.failure(Exception("Location permission not granted")))
        }
    }
}
