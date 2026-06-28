package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class ZScoreResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val zScore: Double,
    val currentPrice: Double,
    val meanPrice: Double,
    val stdDev: Double,
    val window: Int,
    val metadata: ZScoreMetadata
)

@Serializable
data class ZScoreMetadata(
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
