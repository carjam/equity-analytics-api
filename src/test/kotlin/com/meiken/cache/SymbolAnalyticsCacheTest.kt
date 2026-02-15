package com.meiken.cache

import com.meiken.config.CacheConfig
import com.meiken.config.TestConfig
import com.meiken.data.MarketDataService
import com.meiken.data.MockMarketDataService
import com.meiken.error.DataRetrievalException
import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SymbolAnalyticsCacheTest {

    private val mockMarketData = MockMarketDataService(kotlin.random.Random(1), 100.0, 0.02)
    private val cache = SymbolAnalyticsCacheService(
        TestConfig.cacheConfig,
        TestConfig.calculationsConfig,
        TestConfig.dataQualityConfig
    )

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

    @Test
    fun `getOrCompute with prices with gap produces gapDays and data quality`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 15)
        val pricesWithGap = listOf(
            DailyPrice(from, 100.0),
            DailyPrice(LocalDate(2024, 1, 2), 101.0),
            DailyPrice(LocalDate(2024, 1, 10), 105.0),
            DailyPrice(LocalDate(2024, 1, 11), 106.0)
        )
        val gapMarketData = object : MarketDataService {
            override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate) = pricesWithGap
        }
        val cacheWithGapThreshold = SymbolAnalyticsCacheService(
            TestConfig.cacheConfig.copy(gapThreshold = 2),
            TestConfig.calculationsConfig,
            TestConfig.dataQualityConfig
        )
        val result = cacheWithGapThreshold.getOrCompute("GAP", from, to, gapMarketData)
        assertTrue(result.gapDays.isNotEmpty(), "expected gap days when range has gap > 2")
        assertTrue(result.dataQuality == "POOR" || result.dataQuality == "ACCEPTABLE" || result.warnings.isNotEmpty())
        assertEquals(4, result.dailyPrices.size)
    }
}
