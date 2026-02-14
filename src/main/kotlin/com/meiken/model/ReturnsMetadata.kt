package com.meiken.model

import kotlinx.serialization.Serializable

@Serializable
data class ReturnsMetadata(
    val dataPoints: Int,
    val source: String
)
