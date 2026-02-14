package com.meiken.service

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.MarketDataService
import com.meiken.model.Returns
import com.meiken.model.ReturnsMetadata
import com.meiken.util.validateDateRange
import kotlinx.datetime.LocalDate

/**
 * Default implementation: validates date range, gets [SymbolAnalytics] from [SymbolAnalyticsCacheService]
 * (cache hit returns immediately; cache miss triggers one fetch and computation, then cache storage).
 * Returns [Returns] using the cached daily returns—no separate API call or recalculation for this endpoint.
 */
class ReturnsServiceImpl(
    private val analyticsCache: SymbolAnalyticsCacheService,
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365,
    private val sourceName: String = "market_data"
) : ReturnsService {

    /** Returns daily close-to-close returns from cached analytics (close-of-day prices only). */
    override suspend fun calculateReturns(symbol: String, fromDate: LocalDate, toDate: LocalDate): Returns {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        return Returns(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            dailyReturns = analytics.dailyReturns,
            metadata = ReturnsMetadata(
                dataPoints = analytics.dailyReturns.size,
                source = sourceName
            )
        )
    }
}
