package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class MomentumResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val momentum: List<RateOfChangeData>,
    val metadata: MomentumMetadata
)

@Serializable
data class RateOfChangeData(
    val date: LocalDate,
    val rateOfChange: Double,
    val lookback: Int
)

@Serializable
data class MomentumMetadata(
    val dataPoints: Int,
    val lookbacks: List<Int>,
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
