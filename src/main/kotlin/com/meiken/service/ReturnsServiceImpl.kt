package com.meiken.service

import com.meiken.calculator.FinancialCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.Returns
import com.meiken.model.ReturnsMetadata
import com.meiken.util.validateDateRange
import kotlinx.datetime.LocalDate

class ReturnsServiceImpl(
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365,
    private val sourceName: String = "market_data"
) : ReturnsService {

    override suspend fun calculateReturns(symbol: String, fromDate: LocalDate, toDate: LocalDate): Returns {
        validateDateRange(fromDate, toDate, maxDays)
        val prices = marketDataService.getHistoricalPrices(symbol, fromDate, toDate)
        val dailyReturns = FinancialCalculations.calculateDailyReturns(prices)
        return Returns(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            dailyReturns = dailyReturns,
            metadata = ReturnsMetadata(
                dataPoints = dailyReturns.size,
                source = sourceName
            )
        )
    }
}
