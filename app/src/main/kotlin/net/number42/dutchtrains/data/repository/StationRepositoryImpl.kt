package net.number42.dutchtrains.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.api.NsStationsService
import net.number42.dutchtrains.data.api.dto.StationV3Dto
import net.number42.dutchtrains.domain.model.Station
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StationRepositoryImpl @Inject constructor(
    private val stationsService: NsStationsService,
) : StationRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var stationCache: List<Station> = emptyList()

    init {
        scope.launch { refreshCache() }
    }

    private suspend fun refreshCache() {
        runCatching {
            stationsService.getAllStations()
                .payload
                .filter { it.country?.uppercase() == "NL" }
                .map { it.toDomain() }
        }.getOrNull()?.let { stations ->
            stationCache = stations
        }
    }

    override suspend fun searchStations(query: String): List<Station> {
        val cache = stationCache
        if (cache.isNotEmpty()) {
            val q = query.lowercase().trim()
            return cache.filter { station ->
                station.name.lowercase().contains(q) || station.code.lowercase().startsWith(q)
            }.take(10)
        }
        // Cache not ready yet — fall back to live API
        return runCatching {
            stationsService.searchStations(query = query)
                .payload
                .filter { it.country?.uppercase() == "NL" }
                .map { it.toDomain() }
        }.getOrDefault(emptyList())
    }

    override suspend fun getNearestStation(lat: Double, lng: Double): Station? =
        runCatching {
            stationsService.getNearestStations(lat = lat, lng = lng, limit = 1)
                .payload.firstOrNull()?.toDomain()
        }.getOrNull()

    private fun StationV3Dto.toDomain() = Station(
        code = id.code,
        uicCode = id.uicCode,
        name = names.long,
        lat = location.lat,
        lng = location.lng,
    )
}
