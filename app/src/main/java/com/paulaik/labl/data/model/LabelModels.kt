package com.paulaik.labl.data.model

/**
 * A single classified symbol returned by the backend /labels endpoint.
 * [validated]: 0 = pending, 1 = approved, -1 = rejected
 */
data class SymbolLabel(
    val id: String,
    val sessionId: String,
    val applianceType: String,
    val s3Key: String,
    val cropS3Key: String?,
    val name: String,
    val description: String,
    val category: String,
    val confidence: String,
    val validated: Int,
    val createdAt: Long
)
