package com.meiken.api

import kotlinx.serialization.Serializable

/**
 * Overall health status of the application.
 * Use "ok" when all dependencies are up, "degraded" when some are failing, "unhealthy" when critical.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val message: String,
    val timestamp: String,
    val version: String = "0.1.0",
    val dependencies: List<DependencyStatus> = emptyList()
)

/**
 * Status of a single dependency (DB, external API, cache, etc.).
 */
@Serializable
data class DependencyStatus(
    val name: String,
    val status: String,
    val message: String? = null,
    val details: Map<String, String>? = null
)
