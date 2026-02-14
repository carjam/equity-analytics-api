package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate

interface MarketDataService {
    suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice>
}
