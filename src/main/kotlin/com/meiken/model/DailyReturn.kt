package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DailyReturn(
    val date: LocalDate,
    val returnValue: Double
)
