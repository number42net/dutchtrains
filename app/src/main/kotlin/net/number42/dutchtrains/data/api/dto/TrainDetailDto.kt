package net.number42.dutchtrains.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrainDetailResponse(
    val lengte: Int? = null,
    val materieeldelen: List<TreinDeelDto> = emptyList(),
)

@Serializable
data class TreinDeelDto(
    val materieelnummer: Int? = null,
    @SerialName("type") val type: String? = null,
    val afbeelding: String? = null,
    val materieelType: String? = null,
    val afbeeldingsSpecs: AfbeeldingSpecsDto? = null,
)

@Serializable
data class AfbeeldingSpecsDto(
    val imageUrl: String? = null,
)
