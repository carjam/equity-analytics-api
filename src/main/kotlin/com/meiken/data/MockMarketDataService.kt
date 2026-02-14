package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import kotlin.random.Random

/**
 * Implements [MarketDataService] with mock close-of-day data: one closing price per calendar day,
 * starting price $100, random walk +/-2% per day. Used for testing.
 */
class MockMarketDataService(
    private val random: Random = Random.Default,
    private val initialPrice: Double = 100.0,
    private val maxDailyChangeFraction: Double = 0.02
) : MarketDataService {

    /** Generates synthetic close-of-day prices: one closing price per calendar day in range, random walk from [initialPrice] with daily change ±[maxDailyChangeFraction]. */
    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        if (fromDate > toDate) return emptyList()
        val fromEpoch = fromDate.toEpochDays()
        val toEpoch = toDate.toEpochDays()
        val prices = mutableListOf<DailyPrice>()
        var price = initialPrice
        var epoch = fromEpoch
        while (epoch <= toEpoch) {
            val current = LocalDate.fromEpochDays(epoch)
            prices.add(DailyPrice(date = current, close = price))
            val change = (random.nextDouble() * 2 - 1) * maxDailyChangeFraction
            price = (price * (1 + change)).coerceIn(0.01, Double.MAX_VALUE)
            epoch++
        }
        return prices
    }
}
