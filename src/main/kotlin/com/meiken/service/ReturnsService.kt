package com.meiken.service

import com.meiken.model.Returns
import kotlinx.datetime.LocalDate

/** Service for computing daily returns over a date range for a given symbol. */
interface ReturnsService {
    /** Fetches historical prices and returns daily returns (percentage change day-over-day). */
    suspend fun calculateReturns(symbol: String, fromDate: LocalDate, toDate: LocalDate): Returns
}
