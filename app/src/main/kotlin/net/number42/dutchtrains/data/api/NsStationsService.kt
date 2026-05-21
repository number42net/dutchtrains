package net.number42.dutchtrains.data.api

import net.number42.dutchtrains.data.api.dto.StationsV3Response
import retrofit2.http.GET
import retrofit2.http.Query

interface NsStationsService {

    @GET("nsapp-stations/v3")
    suspend fun searchStations(
        @Query("q") query: String,
        @Query("countryCodes") countryCodes: String = "nl",
        @Query("limit") limit: Int = 10,
    ): StationsV3Response

    @GET("nsapp-stations/v3")
    suspend fun getAllStations(
        @Query("countryCodes") countryCodes: String = "nl",
        @Query("limit") limit: Int = 600,
    ): StationsV3Response

    @GET("nsapp-stations/v3/nearest")
    suspend fun getNearestStations(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("limit") limit: Int = 1,
    ): StationsV3Response
}
