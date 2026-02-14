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
    val source: String = "market_data"
)
