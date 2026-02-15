package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Date range limits from application.conf (meiken.dateRanges).
 * Used for validating from_date/to_date span (e.g. max 365 days); override per env via DATE_RANGE_MAX_DAYS.
 */
data class DateRangesConfig(
    val maxDays: Int
) {
    companion object {
        fun from(config: ApplicationConfig): DateRangesConfig {
            val dr = try {
                config.config("meiken").config("dateRanges")
            } catch (_: Exception) {
                return DateRangesConfig(maxDays = 365)
            }
            return DateRangesConfig(
                maxDays = dr.propertyOrNull("maxDays")?.getString()?.toIntOrNull() ?: 365
            )
        }
    }
}
