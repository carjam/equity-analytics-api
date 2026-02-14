package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class SharpeResponse(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val sharpe: Double,
    val riskFreeRate: Double,
    val metadata: SharpeMetadata
)

@Serializable
data class SharpeMetadata(
    val dataPoints: Int,
    val source: String = "market_data"
)
