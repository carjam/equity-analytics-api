package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Cache and analytics data-quality thresholds from application.conf (meiken.cache).
 * Used by [SymbolAnalyticsCacheService] for TTL, size, staleness labels, and data-quality rules.
 * Values can be overridden per environment via env vars (e.g. CACHE_TTL, CACHE_RECENT_DATES_DAYS).
 */
data class CacheConfig(
    val ttlSeconds: Long,
    val maxSize: Int,
    val staleSeconds: Long,
    val oneHourSeconds: Long,
    val gapThreshold: Int,
    val recentDatesDays: Int,
    val sparseRatio: Double
) {
    companion object {
        fun from(config: ApplicationConfig): CacheConfig {
            val cache = try {
                config.config("meiken").config("cache")
            } catch (_: Exception) {
                return CacheConfig(
                    ttlSeconds = 3600L,
                    maxSize = 1000,
                    staleSeconds = 86400L,
                    oneHourSeconds = 3600L,
                    gapThreshold = 5,
                    recentDatesDays = 5,
                    sparseRatio = 0.5
                )
            }
            return CacheConfig(
                ttlSeconds = cache.propertyOrNull("ttl")?.getString()?.toLongOrNull() ?: 3600L,
                maxSize = cache.propertyOrNull("maxSize")?.getString()?.toIntOrNull() ?: 1000,
                staleSeconds = cache.propertyOrNull("staleSeconds")?.getString()?.toLongOrNull() ?: 86400L,
                oneHourSeconds = cache.propertyOrNull("oneHourSeconds")?.getString()?.toLongOrNull() ?: 3600L,
                gapThreshold = cache.propertyOrNull("gapThreshold")?.getString()?.toIntOrNull() ?: 5,
                recentDatesDays = cache.propertyOrNull("recentDatesDays")?.getString()?.toIntOrNull() ?: 5,
                sparseRatio = cache.propertyOrNull("sparseRatio")?.getString()?.toDoubleOrNull() ?: 0.5
            )
        }
    }
}
