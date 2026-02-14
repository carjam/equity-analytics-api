package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate

/** Abstraction for fetching daily closing prices for a symbol over a date range. */
interface MarketDataService {
    /**
     * Returns close-of-day prices in the range [fromDate, toDate], sorted by date.
     * Exactly one price per calendar day per ticker (daily close); used for day-over-day returns and analytics.
     */
    suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice>
}
