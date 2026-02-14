package com.meiken.model

import kotlinx.serialization.Serializable

@Serializable
data class VolatilityData(
    val daily: Double,
    val annualized: Double
)
