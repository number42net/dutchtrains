package net.number42.dutchtrains.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit

data class Leg(
    val travelType: String,              // PUBLIC_TRANSIT, WALK, TRANSFER
    val name: String,                    // "IC 1234"
    val category: String,                // "IC", "SPR", "ICE"
    val journeyDetailRef: String,        // train number → material API
    val direction: String,
    val originName: String,
    val plannedDeparture: Instant,
    val actualDeparture: Instant,
    val plannedDepartureTrack: String?,
    val actualDepartureTrack: String?,
    val destinationName: String,
    val plannedArrival: Instant,
    val actualArrival: Instant,
    val plannedArrivalTrack: String?,
    val actualArrivalTrack: String?,
    val cancelled: Boolean,
    val crowdForecast: String?,
    val originArrival: Instant? = null,  // when the train arrives at the departure platform
    val material: TrainMaterial? = null,
) {
    val delayMinutes: Long
        get() = ChronoUnit.MINUTES.between(plannedDeparture, actualDeparture)

    val isDepartureTrackChanged: Boolean
        get() = plannedDepartureTrack != null
            && actualDepartureTrack != null
            && plannedDepartureTrack != actualDepartureTrack
}
