package com.paulaik.labl.data.model

/**
 * A single appliance symbol identified in the camera frame.
 */
data class Symbol(
    val id: String,
    val name: String,
    val category: SymbolCategory,
    val meaning: String,
    val instructions: String,
    val position: SymbolPosition = SymbolPosition.CENTER,
    val confidence: Confidence = Confidence.MEDIUM
)

enum class SymbolCategory(val displayName: String, val emoji: String) {
    WASHING("Washing", "🫧"),
    DRYING("Drying", "💨"),
    IRONING("Ironing", "👕"),
    BLEACHING("Bleaching", "⚗️"),
    DISHWASHER("Dishwasher", "🍽️"),
    OVEN("Oven", "🔥"),
    OTHER("Appliance", "ℹ️")
}

enum class SymbolPosition(val xFraction: Float, val yFraction: Float) {
    TOP_LEFT(0.20f, 0.18f),
    TOP_CENTER(0.50f, 0.18f),
    TOP_RIGHT(0.80f, 0.18f),
    MIDDLE_LEFT(0.20f, 0.50f),
    CENTER(0.50f, 0.50f),
    MIDDLE_RIGHT(0.80f, 0.50f),
    BOTTOM_LEFT(0.20f, 0.78f),
    BOTTOM_CENTER(0.50f, 0.78f),
    BOTTOM_RIGHT(0.80f, 0.78f)
}

enum class Confidence { HIGH, MEDIUM, LOW }

data class AnalysisResult(
    val symbols: List<Symbol>,
    val timestamp: Long = System.currentTimeMillis()
)
