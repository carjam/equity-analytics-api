package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Performance configuration from application.conf (meiken.performance).
 */
data class PerformanceConfig(
    val compression: CompressionSettings
) {
    data class CompressionSettings(
        val enabled: Boolean,
        val minSize: Int,
        val level: Int
    )

    companion object {
        fun from(config: ApplicationConfig): PerformanceConfig {
            val compression = try {
                val perf = config.config("meiken").config("performance").config("compression")
                CompressionSettings(
                    enabled = perf.propertyOrNull("enabled")?.getString()?.toBooleanStrictOrNull() ?: true,
                    minSize = perf.propertyOrNull("minSize")?.getString()?.toIntOrNull() ?: 1024,
                    level = perf.propertyOrNull("level")?.getString()?.toIntOrNull() ?: 6
                )
            } catch (_: Exception) {
                CompressionSettings(enabled = true, minSize = 1024, level = 6)
            }
            return PerformanceConfig(compression = compression)
        }
    }
}
