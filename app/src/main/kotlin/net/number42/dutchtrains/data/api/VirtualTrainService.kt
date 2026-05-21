package net.number42.dutchtrains.data.api

import net.number42.dutchtrains.data.api.dto.TrainDetailResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface VirtualTrainService {

    @GET("virtual-train-api/api/v1/trein/{id}")
    suspend fun getTrainDetail(
        @Path("id") id: String,
    ): TrainDetailResponse
}
