package net.number42.dutchtrains.service

import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class FollowSnapshot(val legs: List<LegFollowSnapshot>)

internal data class LegFollowSnapshot(
    val legId: String,
    val legLabel: String,
    val departureStation: String,
    val arrivalStation: String,
    val departureTime: String,
    val arrivalTime: String,
    val actualDepartureTrack: String?,
    val platformArrivalTime: String?,
    val platformArrivalInstant: Instant?,
    val platformArrivalReached: Boolean,
    val materialSummary: String,
    val cancelled: Boolean,
)

internal data class NotificationEvents(
    val platformChanges: Boolean,
    val departureTime: Boolean,
    val arrivalTime: Boolean,
    val platformArrivalChanges: Boolean,
    val materialChanges: Boolean,
)

internal fun detectChanges(
    old: FollowSnapshot,
    new: FollowSnapshot,
    events: NotificationEvents,
): List<TrainChange> = buildList {
    val oldById = old.legs.associateBy { it.legId }
    val newById = new.legs.associateBy { it.legId }

    for ((legId, newLeg) in newById) {
        val oldLeg = oldById[legId] ?: continue
        val prefix = if (new.legs.size > 1) "${newLeg.legLabel}: " else ""

        if (events.departureTime && oldLeg.departureTime != newLeg.departureTime) {
            val field = if (new.legs.size > 1) "${prefix}Departure from ${newLeg.departureStation}" else "Departure"
            add(TrainChange(TrainEventType.DEPARTURE_TIME, field, oldLeg.departureTime, newLeg.departureTime))
        }
        if (events.arrivalTime && oldLeg.arrivalTime != newLeg.arrivalTime) {
            val field = if (new.legs.size > 1) "${prefix}Arrival at ${newLeg.arrivalStation}" else "Destination arrival"
            add(TrainChange(TrainEventType.ARRIVAL_TIME, field, oldLeg.arrivalTime, newLeg.arrivalTime))
        }
        if (events.platformChanges && oldLeg.actualDepartureTrack != newLeg.actualDepartureTrack) {
            add(TrainChange(TrainEventType.PLATFORM_CHANGES, "${prefix}Platform",
                oldLeg.actualDepartureTrack ?: "?", newLeg.actualDepartureTrack ?: "?"))
        }
        if (events.platformArrivalChanges) {
            val becameUnknownNearExpected = oldLeg.platformArrivalInstant != null
                && newLeg.platformArrivalInstant == null
                && ChronoUnit.MINUTES.between(oldLeg.platformArrivalInstant, Instant.now()) in -3L..5L
            if (becameUnknownNearExpected) {
                add(TrainChange(TrainEventType.PLATFORM_ARRIVAL_CHANGES, "${prefix}Arrived at platform", "not arrived", "arrived"))
            } else if (oldLeg.platformArrivalTime != newLeg.platformArrivalTime) {
                add(TrainChange(TrainEventType.PLATFORM_ARRIVAL_CHANGES, "${prefix}Platform arrival",
                    oldLeg.platformArrivalTime ?: "??:??", newLeg.platformArrivalTime ?: "??:??"))
            }
            if (!oldLeg.platformArrivalReached && newLeg.platformArrivalReached) {
                add(TrainChange(TrainEventType.PLATFORM_ARRIVAL_CHANGES, "${prefix}Arrived at platform", "not arrived", "arrived"))
            }
        }
        if (events.materialChanges && oldLeg.materialSummary != newLeg.materialSummary) {
            add(TrainChange(TrainEventType.MATERIAL_CHANGES, "${prefix}Material",
                oldLeg.materialSummary.ifBlank { "unknown" }, newLeg.materialSummary.ifBlank { "unknown" }))
        }
        if (!oldLeg.cancelled && newLeg.cancelled) {
            add(TrainChange(TrainEventType.DEPARTURE_TIME, "${prefix}Status", "Running", "CANCELLED"))
        }
    }
}
