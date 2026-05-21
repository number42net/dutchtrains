package net.number42.dutchtrains.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class StationsV3Response(
    val payload: List<StationV3Dto> = emptyList(),
)

@Serializable
data class StationV3Dto(
    val id: StationIdentificationDto,
    val names: StationNamesDto,
    val location: CoordinateDto,
    val country: String? = null,
)

@Serializable
data class StationIdentificationDto(
    val code: String,
    val uicCode: String,
)

@Serializable
data class StationNamesDto(
    val long: String,
    val medium: String? = null,
    val short: String? = null,
)

@Serializable
data class CoordinateDto(
    val lat: Double,
    val lng: Double,
)
