package net.number42.dutchtrains.data.repository

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.number42.dutchtrains.data.api.NsTripsService
import net.number42.dutchtrains.data.api.VirtualTrainService
import net.number42.dutchtrains.data.api.dto.ArrivalDto
import net.number42.dutchtrains.data.api.dto.LegDto
import net.number42.dutchtrains.data.api.dto.TripDto
import net.number42.dutchtrains.domain.model.Leg
import net.number42.dutchtrains.domain.model.Station
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripsService: NsTripsService,
    private val virtualTrainService: VirtualTrainService,
) : TripRepository {

    private companion object {
        private const val NS_IMAGE_BASE_URL = "https://gateway.apiportal.ns.nl/virtual-train-api/v1/images"
        private val NS_QUERY_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")
            .withZone(ZoneId.of("Europe/Amsterdam"))
    }

    // Cache: journeyDetailRef -> material (static per material id)
    private val materialCache = ConcurrentHashMap<String, TrainMaterial>()

    override suspend fun findTrips(from: Station, to: Station, displayWindowMinutes: Int): Result<List<Trip>> =
        runCatching {
            coroutineScope {
                val windowMinutes = displayWindowMinutes.coerceIn(30, 180)
                val queryOffsets = (0..windowMinutes step 60).toList()
                val tripDeferredCalls = queryOffsets.map { offsetMinutes ->
                    async {
                        if (offsetMinutes == 0) {
                            tripsService.getTrips(fromStation = from.code, toStation = to.code)
                        } else {
                            val requestIso = NS_QUERY_TIME_FORMATTER.format(
                                Instant.now().plus(offsetMinutes.toLong(), ChronoUnit.MINUTES),
                            )
                            tripsService.getTrips(fromStation = from.code, toStation = to.code, dateTime = requestIso)
                        }
                    }
                }
                val arrivalsDeferred = async {
                    try {
                        tripsService.getArrivals(station = from.code).payload?.arrivals ?: emptyList()
                    } catch (e: Exception) {
                        Log.w("TripRepo", "arrivals fetch failed: ${e.message}")
                        emptyList()
                    }
                }
                val allTrips = tripDeferredCalls.flatMap { it.await().trips }
                val arrivals = arrivalsDeferred.await()
                Log.d("TripRepo", "arrivals for ${from.code}: ${arrivals.size}")

                val seenCtx = HashSet<String>()
                allTrips
                    .filter { seenCtx.add(it.ctxRecon) }
                    .map { it.toDomain(arrivals) }
            }
        }

    override suspend fun getUpdatedTrip(ctxRecon: String, departureStationCode: String?): Result<Trip> =
        runCatching {
            val arrivals = if (!departureStationCode.isNullOrBlank()) {
                runCatching {
                    tripsService.getArrivals(station = departureStationCode).payload?.arrivals ?: emptyList()
                }.getOrElse {
                    Log.w("TripRepo", "arrivals fetch failed for follow: ${it.message}")
                    emptyList()
                }
            } else {
                emptyList()
            }
            tripsService.getTrip(ctxRecon).toDomain(arrivals)
        }

    override suspend fun getMaterial(journeyDetailRef: String, stationCode: String): TrainMaterial? {
        materialCache[journeyDetailRef]?.let { return it }
        Log.d("TripRepo", "getMaterial: id=$journeyDetailRef")
        return runCatching {
            val dto = virtualTrainService.getTrainDetail(id = journeyDetailRef)
            val parts = dto.materieeldelen.map { part ->
                "${part.materieelnummer ?: ""} (${part.type ?: ""})".trim()
            }
            val resolvedImageUrl = dto.materieeldelen.firstNotNullOfOrNull { part ->
                part.afbeeldingsSpecs?.imageUrl
                    ?: part.afbeelding?.takeIf { it.isNotBlank() }?.let { image ->
                        when {
                            image.startsWith("http://") || image.startsWith("https://") -> image
                            !part.materieelType.isNullOrBlank() -> "$NS_IMAGE_BASE_URL/${part.materieelType}/$image"
                            else -> "$NS_IMAGE_BASE_URL/$image"
                        }
                    }
            }
            val result = TrainMaterial(
                length = dto.lengte,
                parts = parts,
                imageUrl = resolvedImageUrl,
            )
            Log.d("TripRepo", "getMaterial result: lengte=${result.length} parts=${result.parts} imageUrl=${result.imageUrl}")
            result
        }.onFailure { e ->
            Log.e("TripRepo", "getMaterial failed: id=$journeyDetailRef", e)
        }.getOrNull()?.also { material -> materialCache[journeyDetailRef] = material }
    }

    private fun TripDto.toDomain(arrivals: List<ArrivalDto>): Trip = Trip(
        ctxRecon = ctxRecon,
        transfers = transfers,
        legs = legs.map { it.toDomain(arrivals) },
        plannedDurationMinutes = plannedDurationInMinutes,
        actualDurationMinutes = actualDurationInMinutes,
        status = status,
    )

    private fun LegDto.toDomain(arrivals: List<ArrivalDto>): Leg {
        val resolvedRef = product?.number ?: journeyDetailRef ?: ""
        val departureTrack = origin.actualTrack ?: origin.plannedTrack
        val departureTime = (origin.actualDateTime ?: origin.plannedDateTime).toInstant()

        // Match an arrival at the same track that arrived 1–30 minutes before this departure
        val originArrival = arrivals.firstOrNull { arrival ->
            val arrTrack = arrival.actualTrack ?: arrival.plannedTrack
            if (departureTrack != null && arrTrack == departureTrack) {
                val arrTime = (arrival.actualDateTime ?: arrival.plannedDateTime)
                    ?.runCatching { toInstant() }?.getOrNull()
                if (arrTime != null) {
                    val diffMin = ChronoUnit.MINUTES.between(arrTime, departureTime)
                    diffMin in 1..30
                } else false
            } else false
        }?.let { arrival ->
            (arrival.actualDateTime ?: arrival.plannedDateTime)
                ?.runCatching { toInstant() }?.getOrNull()
        }

        return Leg(
            travelType = travelType,
            name = name ?: "",
            category = product?.categoryCode ?: "",
            journeyDetailRef = resolvedRef,
            direction = direction ?: destination.name,
            originName = origin.name,
            plannedDeparture = origin.plannedDateTime.toInstant(),
            actualDeparture = (origin.actualDateTime ?: origin.plannedDateTime).toInstant(),
            plannedDepartureTrack = origin.plannedTrack,
            actualDepartureTrack = origin.actualTrack,
            destinationName = destination.name,
            plannedArrival = destination.plannedDateTime.toInstant(),
            actualArrival = (destination.actualDateTime ?: destination.plannedDateTime).toInstant(),
            plannedArrivalTrack = destination.plannedTrack,
            actualArrivalTrack = destination.actualTrack,
            cancelled = cancelled,
            crowdForecast = crowdForecast,
            originArrival = originArrival,
        )
    }

}

private fun String.toInstant(): Instant {
    // NS API returns compact offsets like +0200; java.time requires +02:00
    val normalized = replace(Regex("""([+-]\d{2})(\d{2})$"""), "$1:$2")
    return Instant.parse(normalized)
}
