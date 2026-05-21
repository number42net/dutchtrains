package net.number42.dutchtrains.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.number42.dutchtrains.data.api.NsTripsService
import net.number42.dutchtrains.data.api.VirtualTrainService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TripRepositoryImplTest {

    private lateinit var server: MockWebServer
    private lateinit var tripsService: NsTripsService
    private lateinit var virtualTrainService: VirtualTrainService
    private lateinit var repo: TripRepositoryImpl

    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        tripsService = retrofit.create(NsTripsService::class.java)
        virtualTrainService = retrofit.create(VirtualTrainService::class.java)
        repo = TripRepositoryImpl(tripsService, virtualTrainService)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // -------------------------------------------------------------------------
    // originArrival matching
    // -------------------------------------------------------------------------

    @Test
    fun `originArrival is set when arrival matches track and is 5 min before departure`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        val arrivalTime = dep.minus(5, ChronoUnit.MINUTES)

        server.enqueue(MockResponse().setBody(arrivalsJson(track = "4b", arrivalTime = arrivalTime)))
        server.enqueue(MockResponse().setBody(tripJson(depTrack = "4b", depTime = dep)))

        val trip = repo.getUpdatedTrip("ctx-test", "ASD").getOrThrow()
        val leg = trip.publicLegs.first()

        assertNotNull("originArrival should be set", leg.originArrival)
        // Allow 1 second tolerance for timestamp rounding
        assertTrue(
            "originArrival should be close to arrivalTime",
            ChronoUnit.SECONDS.between(arrivalTime, leg.originArrival!!).let { it in -1L..1L },
        )
    }

    @Test
    fun `originArrival is null when no arrivals provided (no fromStationCode)`() = runTest {
        server.enqueue(MockResponse().setBody(tripJson(depTrack = "4b", depTime = Instant.now().plus(10, ChronoUnit.MINUTES))))

        val trip = repo.getUpdatedTrip("ctx-test", null).getOrThrow()
        val leg = trip.publicLegs.first()

        assertNull("originArrival should be null when fromStationCode is missing", leg.originArrival)
    }

    @Test
    fun `originArrival is null when arrival track does not match departure track`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        val arrivalTime = dep.minus(5, ChronoUnit.MINUTES)

        server.enqueue(MockResponse().setBody(arrivalsJson(track = "7a", arrivalTime = arrivalTime)))
        server.enqueue(MockResponse().setBody(tripJson(depTrack = "4b", depTime = dep)))

        val trip = repo.getUpdatedTrip("ctx-test", "ASD").getOrThrow()
        val leg = trip.publicLegs.first()

        assertNull("originArrival should be null when tracks don't match", leg.originArrival)
    }

    @Test
    fun `originArrival is null when arrival is outside 1-30 min window (too early)`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        val arrivalTime = dep.minus(45, ChronoUnit.MINUTES) // 45 min before departure

        server.enqueue(MockResponse().setBody(arrivalsJson(track = "4b", arrivalTime = arrivalTime)))
        server.enqueue(MockResponse().setBody(tripJson(depTrack = "4b", depTime = dep)))

        val trip = repo.getUpdatedTrip("ctx-test", "ASD").getOrThrow()
        assertNull(trip.publicLegs.first().originArrival)
    }

    @Test
    fun `originArrival is null when arrival is at departure time (0 min before)`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        // diffMin = 0, window is 1..30, so this should NOT match
        server.enqueue(MockResponse().setBody(arrivalsJson(track = "4b", arrivalTime = dep)))
        server.enqueue(MockResponse().setBody(tripJson(depTrack = "4b", depTime = dep)))

        val trip = repo.getUpdatedTrip("ctx-test", "ASD").getOrThrow()
        assertNull(trip.publicLegs.first().originArrival)
    }

    @Test
    fun `originArrival picks actual track over planned when actual is set`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        val arrivalTime = dep.minus(5, ChronoUnit.MINUTES)

        // Arrival is on track "4b" (actual), departure has planned "4a" but actual "4b"
        server.enqueue(MockResponse().setBody(arrivalsJson(track = "4b", arrivalTime = arrivalTime)))
        server.enqueue(MockResponse().setBody(tripJson(plannedDepTrack = "4a", actualDepTrack = "4b", depTime = dep)))

        val trip = repo.getUpdatedTrip("ctx-test", "ASD").getOrThrow()
        assertNotNull(trip.publicLegs.first().originArrival)
    }

    // -------------------------------------------------------------------------
    // Basic trip mapping
    // -------------------------------------------------------------------------

    @Test
    fun `getUpdatedTrip maps ctxRecon and leg fields correctly`() = runTest {
        val dep = Instant.now().plus(10, ChronoUnit.MINUTES)
        server.enqueue(MockResponse().setBody(tripJson(depTime = dep, ctxRecon = "my-ctx")))

        val trip = repo.getUpdatedTrip("my-ctx", null).getOrThrow()

        assertEquals("my-ctx", trip.ctxRecon)
        assertEquals(0, trip.transfers)
        val leg = trip.publicLegs.first()
        assertEquals("Amsterdam Centraal", leg.originName)
        assertEquals("Utrecht Centraal", leg.destinationName)
        assertEquals("IC", leg.category)
    }

    @Test
    fun `getUpdatedTrip fails gracefully on HTTP error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = repo.getUpdatedTrip("ctx-test", null)
        assertTrue(result.isFailure)
    }

    // -------------------------------------------------------------------------
    // JSON helpers — produce NS-format timestamps, closely matching real data
    // -------------------------------------------------------------------------

    private val nsDateFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ").withZone(ZoneId.of("Europe/Amsterdam"))

    private fun nsTs(instant: Instant) = nsDateFmt.format(instant)

    private fun arrivalsJson(track: String, arrivalTime: Instant) = """
        {
          "payload": {
            "source": "PPV",
            "arrivals": [
              {
                "origin": "Utrecht Centraal",
                "name": "NS IC 28445",
                "plannedDateTime": "${nsTs(arrivalTime)}",
                "actualDateTime": "${nsTs(arrivalTime)}",
                "plannedTrack": "$track",
                "actualTrack": "$track",
                "product": {
                  "number": "28445",
                  "categoryCode": "IC",
                  "longCategoryName": "Intercity",
                  "operatorName": "NS"
                },
                "cancelled": false,
                "arrivalStatus": "INCOMING"
              }
            ]
          }
        }
    """.trimIndent()

    private fun tripJson(
        ctxRecon: String = "ctx-test",
        depTime: Instant = Instant.now().plus(10, ChronoUnit.MINUTES),
        arrTime: Instant = depTime.plus(32, ChronoUnit.MINUTES),
        plannedDepTrack: String? = "4b",
        actualDepTrack: String? = null,
        depTrack: String = "4b",
    ): String {
        val resolvedPlanned = plannedDepTrack ?: depTrack
        val resolvedActual = actualDepTrack ?: depTrack
        return """
            {
              "ctxRecon": "$ctxRecon",
              "plannedDurationInMinutes": 32,
              "actualDurationInMinutes": 32,
              "transfers": 0,
              "status": "NORMAL",
              "legs": [
                {
                  "idx": "0",
                  "name": "IC 28445",
                  "travelType": "PUBLIC_TRANSIT",
                  "direction": "Arnhem Centraal",
                  "cancelled": false,
                  "journeyDetailRef": "HARP_MM-2|test-ref",
                  "product": {
                    "number": "28445",
                    "categoryCode": "IC",
                    "longCategoryName": "Intercity",
                    "operatorCode": "NS"
                  },
                  "origin": {
                    "name": "Amsterdam Centraal",
                    "plannedDateTime": "${nsTs(depTime)}",
                    "actualDateTime": "${nsTs(depTime)}",
                    "plannedTrack": "$resolvedPlanned",
                    "actualTrack": "$resolvedActual"
                  },
                  "destination": {
                    "name": "Utrecht Centraal",
                    "plannedDateTime": "${nsTs(arrTime)}",
                    "actualDateTime": "${nsTs(arrTime)}",
                    "plannedTrack": "18",
                    "actualTrack": "18"
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
