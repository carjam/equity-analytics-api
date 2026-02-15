package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class BetaResponse(
    val target: String,
    val benchmark: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val beta: Double,
    val metadata: BetaMetadata
)

@Serializable
data class BetaMetadata(
    val dataPoints: Int,
    val source: String = "market_data",
    /** Worst data quality of target/benchmark. */
    val dataQuality: String? = null,
    /** Sum of outlier counts for target and benchmark. */
    val outlierCount: Int? = null,
    /** Sum of missing days for target and benchmark. */
    val missingDays: Int? = null,
    /** Combined warnings from both symbols. */
    val warnings: List<String>? = null
)
