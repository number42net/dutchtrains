package net.number42.dutchtrains.domain.model

data class Trip(
    val ctxRecon: String,
    val transfers: Int,
    val legs: List<Leg>,
    val plannedDurationMinutes: Int,
    val actualDurationMinutes: Int?,
    val status: String?,
) {
    val isDirect: Boolean get() = transfers == 0
    val publicLegs: List<Leg> get() = legs.filter { it.travelType == "PUBLIC_TRANSIT" }
    val firstPublicLeg: Leg? get() = publicLegs.firstOrNull()
}
