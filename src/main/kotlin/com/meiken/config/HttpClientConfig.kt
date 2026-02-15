package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * HTTP client pool settings from application.conf (meiken.httpClient).
 * Used when creating the Alpha Vantage HTTP client; override per env via HTTP_CLIENT_KEEP_ALIVE_MS, etc.
 */
data class HttpClientConfig(
    val keepAliveTimeMs: Long,
    val maxConnectionsPerRoute: Int
) {
    companion object {
        fun from(config: ApplicationConfig): HttpClientConfig {
            val hc = try {
                config.config("meiken").config("httpClient")
            } catch (_: Exception) {
                return HttpClientConfig(keepAliveTimeMs = 60_000L, maxConnectionsPerRoute = 50)
            }
            return HttpClientConfig(
                keepAliveTimeMs = hc.propertyOrNull("keepAliveTimeMs")?.getString()?.toLongOrNull() ?: 60_000L,
                maxConnectionsPerRoute = hc.propertyOrNull("maxConnectionsPerRoute")?.getString()?.toIntOrNull() ?: 50
            )
        }
    }
}
