package com.meiken.data

import com.meiken.model.DailyPrice
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.datetime.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Wraps a [MarketDataService] (e.g. [AlphaVantageService]) with Caffeine cache.
 * Caches close-of-day price lists; key: "market_data:${symbol}:${fromDate}:${toDate}"; TTL 1 hour, max 1000 entries.
 */
class CachingMarketDataService(
    private val delegate: MarketDataService
) : MarketDataService {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(1000)
        .build<String, List<DailyPrice>>()

    /** Returns cached close-of-day price list if present; otherwise delegates to [delegate], caches, and returns. Key = symbol:from:to. */
    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        val key = "market_data:$symbol:$fromDate:$toDate"
        return cache.getIfPresent(key) ?: run {
            val result = delegate.getHistoricalPrices(symbol, fromDate, toDate)
            cache.put(key, result)
            result
        }
    }
}
