package com.meiken.model

import kotlinx.serialization.Serializable

@Serializable
data class ReturnsMetadata(
    val dataPoints: Int,
    val source: String,
    /** Data quality level from validation: GOOD, ACCEPTABLE, or POOR. */
    val dataQuality: String? = null,
    /** Number of return outliers (3-sigma) detected; included in calculations. */
    val outlierCount: Int? = null,
    /** Expected trading days in range minus actual data points (US calendar). */
    val missingDays: Int? = null,
    /** Short warnings (e.g. missing_days=N, outliers=N, sparse_data). */
    val warnings: List<String>? = null
)
