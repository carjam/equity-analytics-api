package com.meiken.service

import com.meiken.model.Returns
import kotlinx.datetime.LocalDate

/** Service for computing daily returns over a date range for a given symbol. */
interface ReturnsService {
    /** Fetches close-of-day prices and returns daily close-to-close returns (one per day; percentage change day-over-day). */
    suspend fun calculateReturns(symbol: String, fromDate: LocalDate, toDate: LocalDate): Returns
}
