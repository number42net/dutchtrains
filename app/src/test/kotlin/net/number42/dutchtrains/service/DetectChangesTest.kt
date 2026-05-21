package net.number42.dutchtrains.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class DetectChangesTest {

    private val allEvents = NotificationEvents(
        platformChanges = true,
        departureTime = true,
        arrivalTime = true,
        platformArrivalChanges = true,
        materialChanges = true,
    )

    private fun leg(
        id: String = "leg1",
        label: String = "IC 28445",
        departureStation: String = "Amsterdam Centraal",
        arrivalStation: String = "Utrecht Centraal",
        departureTime: String = "14:56",
        arrivalTime: String = "15:28",
        track: String? = "4b",
        platformArrivalTime: String? = null,
        platformArrivalInstant: Instant? = null,
        platformArrivalReached: Boolean = false,
        material: String = "",
        cancelled: Boolean = false,
    ) = LegFollowSnapshot(
        legId = id,
        legLabel = label,
        departureStation = departureStation,
        arrivalStation = arrivalStation,
        departureTime = departureTime,
        arrivalTime = arrivalTime,
        actualDepartureTrack = track,
        platformArrivalTime = platformArrivalTime,
        platformArrivalInstant = platformArrivalInstant,
        platformArrivalReached = platformArrivalReached,
        materialSummary = material,
        cancelled = cancelled,
    )

    private fun snap(vararg legs: LegFollowSnapshot) = FollowSnapshot(legs.toList())

    // --- platform arrival: null-transition near expected time ---

    @Test
    fun `platform arrival becomes unknown 1 min after expected fires arrived notification`() {
        val expected = Instant.now().minus(1, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "14:50", platformArrivalInstant = expected))
        val new = snap(leg(platformArrivalTime = null, platformArrivalInstant = null))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("Arrived at platform", changes[0].field)
        assertEquals("arrived", changes[0].to)
    }

    @Test
    fun `platform arrival becomes unknown 2 min before expected fires arrived notification`() {
        val expected = Instant.now().plus(2, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "14:52", platformArrivalInstant = expected))
        val new = snap(leg(platformArrivalTime = null, platformArrivalInstant = null))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("Arrived at platform", changes[0].field)
    }

    @Test
    fun `platform arrival becomes unknown 20 min before expected fires generic change not arrived`() {
        val expected = Instant.now().plus(20, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "15:10", platformArrivalInstant = expected))
        val new = snap(leg(platformArrivalTime = null, platformArrivalInstant = null))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("Platform arrival", changes[0].field)
        assertEquals("??:??", changes[0].to)
    }

    @Test
    fun `platform arrival becomes unknown 15 min after expected fires generic change not arrived`() {
        val expected = Instant.now().minus(15, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "14:35", platformArrivalInstant = expected))
        val new = snap(leg(platformArrivalTime = null, platformArrivalInstant = null))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("Platform arrival", changes[0].field)
    }

    // --- platform arrival: time-based reached transition ---

    @Test
    fun `platform arrival reached transition fires arrived notification`() {
        val expected = Instant.now().minus(30, ChronoUnit.SECONDS)
        val old = snap(leg(platformArrivalInstant = expected, platformArrivalReached = false))
        val new = snap(leg(platformArrivalInstant = expected, platformArrivalReached = true))

        val changes = detectChanges(old, new, allEvents)

        assertTrue(changes.any { it.field == "Arrived at platform" && it.to == "arrived" })
    }

    @Test
    fun `platform arrival time changes fires generic change`() {
        val t1 = Instant.now().minus(2, ChronoUnit.HOURS)
        val t2 = Instant.now().minus(110, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "14:50", platformArrivalInstant = t1))
        val new = snap(leg(platformArrivalTime = "15:00", platformArrivalInstant = t2))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals(TrainEventType.PLATFORM_ARRIVAL_CHANGES, changes[0].type)
        assertEquals("14:50", changes[0].from)
        assertEquals("15:00", changes[0].to)
    }

    // --- departure / arrival time changes ---

    @Test
    fun `departure time change fires notification`() {
        val old = snap(leg(departureTime = "14:56"))
        val new = snap(leg(departureTime = "15:01"))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals(TrainEventType.DEPARTURE_TIME, changes[0].type)
        assertEquals("Departure", changes[0].field)
        assertEquals("14:56", changes[0].from)
        assertEquals("15:01", changes[0].to)
    }

    @Test
    fun `arrival time change fires notification`() {
        val old = snap(leg(arrivalTime = "15:28"))
        val new = snap(leg(arrivalTime = "15:33"))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals(TrainEventType.ARRIVAL_TIME, changes[0].type)
        assertEquals("Destination arrival", changes[0].field)
    }

    // --- platform track change ---

    @Test
    fun `departure track change fires notification`() {
        val old = snap(leg(track = "4b"))
        val new = snap(leg(track = "7a"))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals(TrainEventType.PLATFORM_CHANGES, changes[0].type)
        assertEquals("Platform", changes[0].field)
        assertEquals("4b", changes[0].from)
        assertEquals("7a", changes[0].to)
    }

    @Test
    fun `track change to null fires notification with question mark`() {
        val old = snap(leg(track = "4b"))
        val new = snap(leg(track = null))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("?", changes[0].to)
    }

    // --- cancellation ---

    @Test
    fun `leg cancelled fires notification`() {
        val old = snap(leg(cancelled = false))
        val new = snap(leg(cancelled = true))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals("CANCELLED", changes[0].to)
    }

    // --- material changes ---

    @Test
    fun `material change fires notification`() {
        val old = snap(leg(material = "2410 (SLT 4)"))
        val new = snap(leg(material = "3014 (SNG 3) · 3037 (SNG 3)"))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertEquals(TrainEventType.MATERIAL_CHANGES, changes[0].type)
    }

    // --- no changes ---

    @Test
    fun `no changes when snapshot is identical`() {
        val s = snap(leg())
        assertEquals(0, detectChanges(s, s, allEvents).size)
    }

    @Test
    fun `unknown leg id in new snapshot is ignored`() {
        val old = snap(leg(id = "leg1"))
        val new = snap(leg(id = "leg2"))

        assertEquals(0, detectChanges(old, new, allEvents).size)
    }

    // --- notification preferences ---

    @Test
    fun `platform arrival changes suppressed when preference is off`() {
        val expected = Instant.now().minus(1, ChronoUnit.MINUTES)
        val old = snap(leg(platformArrivalTime = "14:50", platformArrivalInstant = expected))
        val new = snap(leg(platformArrivalTime = null, platformArrivalInstant = null))

        val changes = detectChanges(old, new, allEvents.copy(platformArrivalChanges = false))

        assertEquals(0, changes.size)
    }

    @Test
    fun `departure time change suppressed when preference is off`() {
        val old = snap(leg(departureTime = "14:56"))
        val new = snap(leg(departureTime = "15:01"))

        val changes = detectChanges(old, new, allEvents.copy(departureTime = false))

        assertEquals(0, changes.size)
    }

    // --- multi-leg trips ---

    @Test
    fun `multi-leg departure change prefixes field with leg label`() {
        val leg1 = leg(id = "l1", label = "IC 28445", departureTime = "14:56")
        val leg2 = leg(id = "l2", label = "SPR 8346", departureTime = "15:45")
        val old = FollowSnapshot(listOf(leg1, leg2))
        val new = FollowSnapshot(listOf(leg1.copy(departureTime = "15:01"), leg2))

        val changes = detectChanges(old, new, allEvents)

        assertEquals(1, changes.size)
        assertTrue(changes[0].field.startsWith("IC 28445: "))
    }

    @Test
    fun `single-leg departure change has no prefix`() {
        val old = snap(leg(departureTime = "14:56"))
        val new = snap(leg(departureTime = "15:01"))

        val changes = detectChanges(old, new, allEvents)

        assertEquals("Departure", changes[0].field)
    }
}
