package net.number42.dutchtrains.service

enum class TrainEventType {
    PLATFORM_CHANGES,
    DEPARTURE_TIME,
    ARRIVAL_TIME,
    PLATFORM_ARRIVAL_CHANGES,
    MATERIAL_CHANGES,
}

data class TrainChange(
    val type: TrainEventType,
    val field: String,
    val from: String,
    val to: String,
)
