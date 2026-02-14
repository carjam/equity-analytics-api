package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CorrelationResponse(
    val ticker1: String,
    val ticker2: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val correlations: List<RollingCorrelation>
)
