package net.number42.dutchtrains.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ArrivalsResponse(
    val payload: ArrivalsPayload? = null,
)

@Serializable
data class ArrivalsPayload(
    val arrivals: List<ArrivalDto> = emptyList(),
)

@Serializable
data class ArrivalDto(
    val plannedTrack: String? = null,
    val actualTrack: String? = null,
    val plannedDateTime: String? = null,
    val actualDateTime: String? = null,
)
