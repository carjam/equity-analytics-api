package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class VolatilityResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val volatility: VolatilityData,
    val metadata: VolatilityMetadata
)

@Serializable
data class VolatilityMetadata(
    val dataPoints: Int,
    val source: String = "market_data"
)
