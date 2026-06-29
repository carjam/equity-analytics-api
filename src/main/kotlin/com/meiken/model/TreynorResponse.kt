package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class TreynorResponse(
    val symbol: String,
    val benchmark: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val treynor: Double,
    val annualizedReturn: Double,
    val beta: Double,
    val riskFreeRate: Double,
    val metadata: TreynorMetadata
)

@Serializable
data class TreynorMetadata(
    val dataPoints: Int,
    val source: String = "market_data",
    /** Worst data quality of symbol/benchmark. */
    val dataQuality: String? = null,
    /** Sum of outlier counts for symbol and benchmark. */
    val outlierCount: Int? = null,
    /** Sum of missing days for symbol and benchmark. */
    val missingDays: Int? = null,
    /** Combined warnings from both symbols. */
    val warnings: List<String>? = null
)
