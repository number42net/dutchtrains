package net.number42.dutchtrains.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class TripsResponse(
    val trips: List<TripDto> = emptyList(),
)

@Serializable
data class TripDto(
    val ctxRecon: String,
    val transfers: Int = 0,
    val legs: List<LegDto> = emptyList(),
    val plannedDurationInMinutes: Int = 0,
    val actualDurationInMinutes: Int? = null,
    val status: String? = null,
)

@Serializable
data class LegDto(
    val travelType: String = "PUBLIC_TRANSIT",
    val name: String? = null,
    val direction: String? = null,
    val origin: StopDto,
    val destination: StopDto,
    val product: ProductDto? = null,
    val journeyDetailRef: String? = null,
    val cancelled: Boolean = false,
    val crowdForecast: String? = null,
    val stops: List<LegStopDto> = emptyList(),
)

@Serializable
data class LegStopDto(
    val plannedArrivalDateTime: String? = null,
    val actualArrivalDateTime: String? = null,
    val plannedDepartureDateTime: String? = null,
)

@Serializable
data class StopDto(
    val name: String,
    val plannedDateTime: String,
    val actualDateTime: String? = null,
    val plannedTrack: String? = null,
    val actualTrack: String? = null,
)

@Serializable
data class ProductDto(
    val categoryCode: String = "",
    val longCategoryName: String? = null,
    val number: String? = null,
)
