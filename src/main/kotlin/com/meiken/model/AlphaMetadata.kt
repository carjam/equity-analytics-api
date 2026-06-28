package com.meiken.model

import kotlinx.serialization.Serializable

@Serializable
data class AlphaMetadata(
    val dataPoints: Int,
    val calculationMethod: String = "jensens_alpha_ols",
    /** Annual risk-free rate used in the OLS regression (e.g. 0.04 = 4%). */
    val riskFreeRate: Double,
    /** OLS beta: systematic sensitivity of target to benchmark over the period. */
    val beta: Double,
    val targetAnnualizedReturn: Double,
    val benchmarkAnnualizedReturn: Double,
    /** Worst data quality of target/benchmark: GOOD, ACCEPTABLE, or POOR. */
    val dataQuality: String? = null,
    /** Sum of outlier counts for target and benchmark returns. */
    val outlierCount: Int? = null,
    /** Sum of missing days for target and benchmark. */
    val missingDays: Int? = null,
    /** Combined warnings from both symbols. */
    val warnings: List<String>? = null
)
