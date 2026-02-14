package com.meiken.data

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarketDataServiceTest {

    private val mockService = MockMarketDataService(
        random = kotlin.random.Random(42),
        initialPrice = 100.0,
        maxDailyChangeFraction = 0.02
    )

    @Test
    fun `MockMarketDataService returns valid data`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 10)
        val prices = mockService.getHistoricalPrices("TEST", from, to)

        assertTrue(prices.isNotEmpty(), "Expected non-empty list")
        assertEquals(10, prices.size, "Expected 10 days (Jan 1-10 inclusive)")
        prices.forEach { price ->
            assertTrue(price.close > 0, "Expected positive close price, got ${price.close}")
            assertNotNull(price.date)
        }
        assertEquals(from, prices.first().date)
        assertEquals(to, prices.last().date)
    }

    @Test
    fun `MockMarketDataService date filtering works`() = runBlocking {
        val from = LocalDate(2024, 2, 5)
        val to = LocalDate(2024, 2, 8)
        val prices = mockService.getHistoricalPrices("TEST", from, to)

        assertEquals(4, prices.size)
        assertEquals(LocalDate(2024, 2, 5), prices[0].date)
        assertEquals(LocalDate(2024, 2, 6), prices[1].date)
        assertEquals(LocalDate(2024, 2, 7), prices[2].date)
        assertEquals(LocalDate(2024, 2, 8), prices[3].date)
    }

    @Test
    fun `MockMarketDataService returns empty when fromDate after toDate`() = runBlocking {
        val from = LocalDate(2024, 1, 10)
        val to = LocalDate(2024, 1, 5)
        val prices = mockService.getHistoricalPrices("TEST", from, to)

        assertTrue(prices.isEmpty())
    }

    @Test
    fun `MockMarketDataService single day returns one price`() = runBlocking {
        val date = LocalDate(2024, 3, 15)
        val prices = mockService.getHistoricalPrices("TEST", date, date)

        assertEquals(1, prices.size)
        assertEquals(date, prices.single().date)
        assertEquals(100.0, prices.single().close)
    }

    @Test
    fun `MockMarketDataService prices are in ascending date order`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 20)
        val prices = mockService.getHistoricalPrices("TEST", from, to)

        for (i in 0 until prices.size - 1) {
            assertTrue(
                prices[i].date.toEpochDays() <= prices[i + 1].date.toEpochDays(),
                "Prices should be in ascending date order"
            )
        }
    }
}
