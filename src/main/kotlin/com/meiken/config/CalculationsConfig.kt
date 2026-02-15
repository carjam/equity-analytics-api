package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Calculation constants from application.conf (meiken.calculations).
 * Used for annualization (e.g. US equity ~252 trading days per year).
 */
data class CalculationsConfig(
    val tradingDaysPerYear: Int
) {
    companion object {
        fun from(config: ApplicationConfig): CalculationsConfig {
            val calc = try {
                config.config("meiken").config("calculations")
            } catch (_: Exception) {
                return CalculationsConfig(tradingDaysPerYear = 252)
            }
            return CalculationsConfig(
                tradingDaysPerYear = calc.propertyOrNull("tradingDaysPerYear")?.getString()?.toIntOrNull() ?: 252
            )
        }
    }
}
