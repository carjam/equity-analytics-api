package com.meiken.model

import kotlinx.serialization.Serializable

@Serializable
data class AlphaMetadata(
    val dataPoints: Int,
    val calculationMethod: String = "annualized_excess_return",
    val targetAnnualizedReturn: Double,
    val benchmarkAnnualizedReturn: Double
)
