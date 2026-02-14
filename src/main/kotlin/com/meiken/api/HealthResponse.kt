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

/**
 * Enhanced health response for production: status, dependencies with latency/size/hit_rate, system metrics.
 */
@Serializable
data class EnhancedHealthResponse(
    val status: String,
    val timestamp: String,
    val version: String,
    val dependencies: List<EnhancedDependencyStatus>,
    val system: SystemStatus
)

@Serializable
data class EnhancedDependencyStatus(
    val name: String,
    val status: String,
    val latency_ms: Long? = null,
    val message: String? = null,
    val size: Long? = null,
    val hit_rate: Double? = null
)

@Serializable
data class SystemStatus(
    val memory_used_mb: Long,
    val memory_max_mb: Long,
    val uptime_seconds: Long
)
