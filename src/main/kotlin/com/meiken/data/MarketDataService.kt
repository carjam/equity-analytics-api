package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate

/** Abstraction for fetching daily closing prices for a symbol over a date range. */
interface MarketDataService {
    /** Returns daily prices (date, close) in the range [fromDate, toDate], sorted by date. */
    suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice>
}
