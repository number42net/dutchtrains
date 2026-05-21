package net.number42.dutchtrains.data.api

import net.number42.dutchtrains.data.api.dto.ArrivalsResponse
import net.number42.dutchtrains.data.api.dto.TripDto
import net.number42.dutchtrains.data.api.dto.TripsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface NsTripsService {

    @GET("reisinformatie-api/api/v3/trips")
    suspend fun getTrips(
        @Query("fromStation") fromStation: String,
        @Query("toStation") toStation: String,
        @Query("dateTime") dateTime: String? = null,
    ): TripsResponse

    @GET("reisinformatie-api/api/v3/trips/trip")
    suspend fun getTrip(
        @Query("ctxRecon") ctxRecon: String,
    ): TripDto

    @GET("reisinformatie-api/api/v2/arrivals")
    suspend fun getArrivals(
        @Query("station") station: String,
        @Query("maxJourneys") maxJourneys: Int = 40,
    ): ArrivalsResponse
}
