package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Returns(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val dailyReturns: List<DailyReturn>,
    val metadata: ReturnsMetadata
)
