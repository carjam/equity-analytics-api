package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * API-level settings from application.conf (meiken.api).
 * Used for response headers (e.g. Cache-Control max-age); override per env via API_CACHE_MAX_AGE_SECONDS.
 */
data class ApiConfig(
    val cacheControlMaxAgeSeconds: Int
) {
    companion object {
        fun from(config: ApplicationConfig): ApiConfig {
            val api = try {
                config.config("meiken").config("api")
            } catch (_: Exception) {
                return ApiConfig(cacheControlMaxAgeSeconds = 300)
            }
            return ApiConfig(
                cacheControlMaxAgeSeconds = api.propertyOrNull("cacheControlMaxAgeSeconds")?.getString()?.toIntOrNull() ?: 300
            )
        }
    }
}
