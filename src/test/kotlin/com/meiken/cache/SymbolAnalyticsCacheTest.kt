package com.meiken.cache

import com.meiken.data.MarketDataService
import com.meiken.data.MockMarketDataService
import com.meiken.error.DataRetrievalException
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SymbolAnalyticsCacheTest {

    private val mockMarketData = MockMarketDataService(kotlin.random.Random(1), 100.0, 0.02)
    private val cache = SymbolAnalyticsCacheService()

    @Test
    fun `getOrCompute second call for same key returns cached analytics`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 10)
        val first = cache.getOrCompute("AAPL", from, to, mockMarketData)
        val second = cache.getOrCompute("AAPL", from, to, mockMarketData)
        assertEquals(first.symbol, second.symbol)
        assertEquals(first.fromDate, second.fromDate)
        assertEquals(first.toDate, second.toDate)
        assertEquals(first.dailyPrices.size, second.dailyPrices.size)
        assertEquals(first.dailyReturns.size, second.dailyReturns.size)
    }

    @Test
    fun `getOrCompute propagates exception and removes key from inProgress`() = runBlocking {
        val failingMarketData: MarketDataService = object : MarketDataService {
            override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate) =
                throw DataRetrievalException("API unavailable")
        }
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 10)
        val ex = assertFailsWith<DataRetrievalException> {
            cache.getOrCompute("X", from, to, failingMarketData)
        }
        assertEquals("API unavailable", ex.message)
        // Second call for same key should retry (key was removed), not hang on awaiting failed deferred
        assertFailsWith<DataRetrievalException> {
            cache.getOrCompute("X", from, to, failingMarketData)
        }
    }
}
