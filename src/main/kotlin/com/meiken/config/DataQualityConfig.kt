package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Data quality validation thresholds from application.conf (meiken.dataQuality).
 * Used by [DataValidator] and by Alpha Vantage integration for price validation.
 */
data class DataQualityConfig(
    val minPrice: Double,
    val maxPrice: Double,
    val maxSingleDayChangePct: Double,
    val outlierSigma: Double
) {
    companion object {
        fun from(config: ApplicationConfig): DataQualityConfig {
            val dq = try {
                config.config("meiken").config("dataQuality")
            } catch (_: Exception) {
                return DataQualityConfig(
                    minPrice = 0.01,
                    maxPrice = 1_000_000.0,
                    maxSingleDayChangePct = 0.50,
                    outlierSigma = 3.0
                )
            }
            return DataQualityConfig(
                minPrice = dq.propertyOrNull("minPrice")?.getString()?.toDoubleOrNull() ?: 0.01,
                maxPrice = dq.propertyOrNull("maxPrice")?.getString()?.toDoubleOrNull() ?: 1_000_000.0,
                maxSingleDayChangePct = dq.propertyOrNull("maxSingleDayChangePct")?.getString()?.toDoubleOrNull() ?: 0.50,
                outlierSigma = dq.propertyOrNull("outlierSigma")?.getString()?.toDoubleOrNull() ?: 3.0
            )
        }
    }
}
