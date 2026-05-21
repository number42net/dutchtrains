package net.number42.dutchtrains.data.repository

import net.number42.dutchtrains.domain.model.Station

interface StationRepository {
    suspend fun searchStations(query: String): List<Station>
    suspend fun getNearestStation(lat: Double, lng: Double): Station?
}
