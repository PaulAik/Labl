package com.paulaik.labl.data.model

import java.util.UUID

enum class ApplianceType(val displayName: String, val emoji: String) {
    WASHING_MACHINE("Washing Machine", "🫧"),
    TUMBLE_DRYER("Tumble Dryer", "💨"),
    OVEN("Oven", "🔥"),
    DISHWASHER("Dishwasher", "🍽️"),
    IRON("Iron", "👕"),
    MICROWAVE("Microwave", "📡"),
    FRIDGE("Fridge / Freezer", "🧊")
}

/**
 * A single guided shot in a training session.
 * [label] is the short name shown in the progress row.
 * [instruction] is the full sentence shown to guide the user.
 */
enum class ShotType(val label: String, val instruction: String) {
    FULL_PANEL(
        "Full Panel",
        "Hold camera straight-on to show the entire control panel clearly"
    ),
    CLOSE_LEFT(
        "Close · Left",
        "Move closer and focus on the left-hand symbols"
    ),
    CLOSE_RIGHT(
        "Close · Right",
        "Move closer and focus on the right-hand symbols"
    ),
    FROM_ABOVE(
        "From Above",
        "Tilt the camera slightly downward from above the appliance"
    ),
    ANGLE_LEFT(
        "45° Left",
        "Step to the left and capture the panel at a 45° angle"
    ),
    ANGLE_RIGHT(
        "45° Right",
        "Step to the right and capture the panel at a 45° angle"
    ),
    WIDE_SHOT(
        "Wide Shot",
        "Step back so the whole appliance is in frame for context"
    )
}

data class TrainingSession(
    val id: String = UUID.randomUUID().toString(),
    val applianceType: ApplianceType,
    val shots: List<ShotType> = ShotType.entries,
    val completedShots: Set<ShotType> = emptySet()
) {
    val totalShots get() = shots.size
    val completedCount get() = completedShots.size
    val isComplete get() = completedShots.size == shots.size
    val nextShot get() = shots.firstOrNull { it !in completedShots }
    val currentIndex get() = completedShots.size
}
