package com.meiken.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** One price per calendar day per ticker: [date] and [close] (close-of-day); optional OHLCV for display. */
@Serializable
data class DailyPrice(
    val date: LocalDate,
    val close: Double,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val volume: Long? = null
)
