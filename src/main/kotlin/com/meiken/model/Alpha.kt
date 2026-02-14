package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class Alpha(
    val target: String,
    val benchmark: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val alpha: Double,
    val metadata: AlphaMetadata
)
