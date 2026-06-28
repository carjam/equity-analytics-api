package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class PriceLevelsResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val levels: PriceLevels,
    val metadata: PriceLevelsMetadata
)

@Serializable
data class PriceLevels(
    val current: Double,
    val currentDate: LocalDate,
    val high52Week: Double,
    val high52WeekDate: LocalDate,
    val low52Week: Double,
    val low52WeekDate: LocalDate,
    val distanceFromHigh: Double,
    val distanceFromLow: Double
)

@Serializable
data class PriceLevelsMetadata(
    val dataPoints: Int,
    val source: String = "market_data",
    /** Data quality: GOOD, ACCEPTABLE, or POOR. */
    val dataQuality: String? = null,
    /** Outlier count (3-sigma) in returns. */
    val outlierCount: Int? = null,
    /** Expected trading days minus actual (US calendar). */
    val missingDays: Int? = null,
    /** Warnings (e.g. missing_days, outliers, sparse_data). */
    val warnings: List<String>? = null
)

/** Result of 52-week level calculation for use in calculations. */
data class PriceLevelsResult(
    val current: Double,
    val currentDate: LocalDate,
    val high52Week: Double,
    val high52WeekDate: LocalDate,
    val low52Week: Double,
    val low52WeekDate: LocalDate,
    val distanceFromHigh: Double,
    val distanceFromLow: Double
)
