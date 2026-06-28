package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CalmarResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val calmar: Double,
    val annualizedReturn: Double,
    val maxDrawdown: Double,
    val metadata: CalmarMetadata
)

@Serializable
data class CalmarMetadata(
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
