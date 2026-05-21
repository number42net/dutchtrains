package net.number42.dutchtrains.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.data.repository.TripRepository
import net.number42.dutchtrains.domain.model.Leg
import net.number42.dutchtrains.domain.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@AndroidEntryPoint
class TrainFollowService : Service() {

    companion object {
        const val ACTION_START = "net.number42.dutchtrains.FOLLOW_START"
        const val ACTION_STOP  = "net.number42.dutchtrains.FOLLOW_STOP"
        const val EXTRA_CTX_RECON   = "ctx_recon"
        const val EXTRA_TRAIN_NAME  = "train_name"
        const val EXTRA_PLANNED_DEP = "planned_departure_iso"
        const val EXTRA_ACTUAL_DEP  = "actual_departure_iso"
        const val EXTRA_FROM_STATION_CODE = "from_station_code"
        const val EXTRA_TRIP_TITLE = "trip_title"
        const val EXTRA_TRIP_TIMES = "trip_times"
        private const val TAG = "TrainFollowService"
        private const val POLL_INTERVAL_MS    = 30_000L
        private const val FOLLOW_DEADLINE_MIN = 15L
    }

    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var appPreferences: AppPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var followJob: Job? = null
    private var lastSnapshot: FollowSnapshot? = null

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Amsterdam"))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ctxRecon   = intent.getStringExtra(EXTRA_CTX_RECON) ?: return START_NOT_STICKY
                val trainName  = intent.getStringExtra(EXTRA_TRAIN_NAME) ?: ""
                val plannedDep = intent.getStringExtra(EXTRA_PLANNED_DEP) ?: return START_NOT_STICKY
                val actualDep  = intent.getStringExtra(EXTRA_ACTUAL_DEP)
                val fromStationCode = intent.getStringExtra(EXTRA_FROM_STATION_CODE)
                val tripTitle = intent.getStringExtra(EXTRA_TRIP_TITLE)
                val tripTimes = intent.getStringExtra(EXTRA_TRIP_TIMES)

                startForeground(
                    NotificationHelper.FOLLOW_NOTIFICATION_ID,
                    notificationHelper.buildFollowingNotification(
                        tripTitle = tripTitle ?: trainName,
                        detail = tripTimes,
                        ctxRecon = ctxRecon,
                    ),
                )
                startFollowing(ctxRecon, trainName, plannedDep, actualDep, fromStationCode, tripTitle, tripTimes)
            }
            ACTION_STOP -> {
                serviceScope.launch { appPreferences.saveFollowedCtxRecon(null) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startFollowing(
        ctxRecon: String,
        trainName: String,
        plannedDepIso: String,
        actualDepIso: String?,
        fromStationCode: String?,
        tripTitle: String?,
        tripTimes: String?,
    ) {
        followJob?.cancel()
        followJob = serviceScope.launch {
            val plannedDep = Instant.parse(plannedDepIso)
            val actualDep  = actualDepIso?.runCatching { Instant.parse(this) }?.getOrNull() ?: plannedDep
            val deadline   = maxOf(plannedDep, actualDep).plus(FOLLOW_DEADLINE_MIN, ChronoUnit.MINUTES)
            Log.d(TAG, "startFollowing train=$trainName planned=$plannedDep actual=$actualDep deadline=$deadline")

            tripRepository.getUpdatedTrip(ctxRecon, fromStationCode).getOrNull()?.let { initialTrip ->
                lastSnapshot = buildSnapshot(initialTrip)
                Log.d(TAG, "initial snapshot: legs=${lastSnapshot?.legs?.size} platformArrival=${lastSnapshot?.legs?.firstOrNull()?.platformArrivalTime}")
            }

            while (Instant.now().isBefore(deadline)) {
                delay(POLL_INTERVAL_MS)
                Log.d(TAG, "polling train=$trainName")
                runCatching {
                    tripRepository.getUpdatedTrip(ctxRecon, fromStationCode).getOrNull()?.let { trip ->
                        val newSnap = buildSnapshot(trip)
                        val prev = lastSnapshot
                        Log.d(TAG, "poll result: platformArrival=${newSnap.legs.firstOrNull()?.platformArrivalTime} prev=${prev?.legs?.firstOrNull()?.platformArrivalTime}")
                        val events = NotificationEvents(
                            platformChanges = appPreferences.notifyPlatformChangesFlow.first(),
                            departureTime = appPreferences.notifyDepartureTimeFlow.first(),
                            arrivalTime = appPreferences.notifyArrivalTimeFlow.first(),
                            platformArrivalChanges = appPreferences.notifyPlatformArrivalChangesFlow.first(),
                            materialChanges = appPreferences.notifyMaterialChangesFlow.first(),
                        )

                        if (prev != null) {
                            val changes = detectChanges(prev, newSnap, events)
                            Log.d(TAG, "detected ${changes.size} change(s): ${changes.map { it.field }}")
                            if (changes.isNotEmpty()) {
                                notificationHelper.postChangeNotification(trainName, ctxRecon, changes)
                                notificationHelper.updateFollowingNotification(
                                    tripTitle = tripTitle ?: trainName,
                                    detail = tripTimes ?: changes.joinToString(" · ") { "${it.field}: ${it.to}" },
                                    ctxRecon = ctxRecon,
                                )
                            }
                        }
                        lastSnapshot = newSnap
                    }
                }
            }
            appPreferences.saveFollowedCtxRecon(null)
            stopSelf()
        }
    }

private fun Leg.toSnapshot(materialSummary: String) = LegFollowSnapshot(
    legId = journeyDetailRef.ifBlank { "$category-$name-$plannedDeparture" },
    legLabel = name.ifBlank { category },
    departureStation = originName,
    arrivalStation = destinationName,
    departureTime = timeFormatter.format(actualDeparture),
    arrivalTime   = timeFormatter.format(actualArrival),
        actualDepartureTrack = actualDepartureTrack,
        platformArrivalTime = originArrival?.let { timeFormatter.format(it) },
        platformArrivalInstant = originArrival,
        platformArrivalReached = originArrival?.let { !Instant.now().isBefore(it) } ?: false,
        materialSummary = materialSummary,
        cancelled     = cancelled,
    )

    private suspend fun buildSnapshot(trip: Trip): FollowSnapshot {
        val legs = trip.publicLegs.map { leg ->
            val materialSummary = if (leg.journeyDetailRef.isNotBlank()) {
                tripRepository.getMaterial(leg.journeyDetailRef, "")
                    ?.parts
                    ?.joinToString(" · ")
                    ?.ifBlank { "" }
                    ?: ""
            } else {
                ""
            }
            leg.toSnapshot(materialSummary = materialSummary)
        }
        return FollowSnapshot(legs = legs)
    }

    override fun onDestroy() {
        followJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
