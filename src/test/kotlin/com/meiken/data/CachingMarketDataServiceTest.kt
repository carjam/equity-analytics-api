package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CachingMarketDataServiceTest {

    @Test
    fun `cache miss delegates to delegate and caches result`() = runBlocking {
        val delegate = MockMarketDataService(kotlin.random.Random(1), 100.0, 0.02)
        val caching = CachingMarketDataService(delegate)
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 5)

        val first = caching.getHistoricalPrices("AAPL", from, to)
        val second = caching.getHistoricalPrices("AAPL", from, to)

        assertEquals(5, first.size)
        assertEquals(5, second.size)
        assertEquals(first, second)
    }

    @Test
    fun `different key causes cache miss`() = runBlocking {
        val delegate = MockMarketDataService(kotlin.random.Random(2), 100.0, 0.02)
        val caching = CachingMarketDataService(delegate)
        val a = caching.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 3))
        val b = caching.getHistoricalPrices("MSFT", LocalDate(2024, 1, 1), LocalDate(2024, 1, 3))
        val c = caching.getHistoricalPrices("AAPL", LocalDate(2024, 1, 5), LocalDate(2024, 1, 7))

        assertEquals(3, a.size)
        assertEquals(3, b.size)
        assertEquals(3, c.size)
    }
}
