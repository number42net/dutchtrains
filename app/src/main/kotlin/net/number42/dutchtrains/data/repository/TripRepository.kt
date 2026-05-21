package net.number42.dutchtrains.data.repository

import net.number42.dutchtrains.domain.model.Station
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.domain.model.Trip

interface TripRepository {
    suspend fun findTrips(from: Station, to: Station, displayWindowMinutes: Int = 120): Result<List<Trip>>
    suspend fun getUpdatedTrip(ctxRecon: String, departureStationCode: String? = null): Result<Trip>
    suspend fun getMaterial(journeyDetailRef: String, stationCode: String): TrainMaterial?
}
