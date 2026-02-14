package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class RollingCorrelation(
    val date: LocalDate,
    val correlation: Double
)
