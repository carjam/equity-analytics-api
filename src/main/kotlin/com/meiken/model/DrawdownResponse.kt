package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DrawdownResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val drawdown: DrawdownData,
    val metadata: DrawdownMetadata
)

@Serializable
data class DrawdownData(
    val maxDrawdown: Double,
    val peakDate: LocalDate,
    val troughDate: LocalDate,
    val peakValue: Double,
    val troughValue: Double,
    val recoveryDate: LocalDate? = null
)

@Serializable
data class DrawdownMetadata(
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

/** Result of max drawdown calculation for use in calculations. */
data class MaxDrawdownResult(
    val maxDrawdown: Double,
    val peakDate: LocalDate,
    val troughDate: LocalDate,
    val peakValue: Double,
    val troughValue: Double
)
