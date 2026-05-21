package net.number42.dutchtrains.domain.model

data class TrainMaterial(
    val length: Int?,               // lengte from NS API (carriage count)
    val parts: List<String>,        // e.g. ["1234 (VIRM_6)", "5678 (VIRM_6)"]
    val imageUrl: String? = null,
) : java.io.Serializable
