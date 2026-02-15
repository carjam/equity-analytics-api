package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Default query/defaults from application.conf (meiken.defaults).
 * Used by API routes when request does not specify a value (e.g. risk-free rate, correlation window).
 */
data class DefaultsConfig(
    val riskFreeRate: Double,
    val correlationWindow: Int
) {
    companion object {
        fun from(config: ApplicationConfig): DefaultsConfig {
            val def = try {
                config.config("meiken").config("defaults")
            } catch (_: Exception) {
                return DefaultsConfig(riskFreeRate = 0.04, correlationWindow = 30)
            }
            return DefaultsConfig(
                riskFreeRate = def.propertyOrNull("riskFreeRate")?.getString()?.toDoubleOrNull() ?: 0.04,
                correlationWindow = def.propertyOrNull("correlationWindow")?.getString()?.toIntOrNull() ?: 30
            )
        }
    }
}
