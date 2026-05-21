package net.number42.dutchtrains.domain.model

data class Station(
    val code: String,      // e.g. "UT" — used in API calls
    val uicCode: String,
    val name: String,      // e.g. "Utrecht Centraal"
    val lat: Double,
    val lng: Double,
)
