package com.meiken.service

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.config.TestConfig
import com.meiken.data.MockMarketDataService
import com.meiken.error.InvalidDateRangeException
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlphaServiceTest {

    private val mockMarketData = MockMarketDataService(kotlin.random.Random(2), 100.0, 0.02)
    private val analyticsCache = SymbolAnalyticsCacheService(
        TestConfig.cacheConfig,
        TestConfig.calculationsConfig,
        TestConfig.dataQualityConfig
    )
    private val alphaService = AlphaServiceImpl(analyticsCache, mockMarketData, maxDays = 365)

    @Test
    fun `calculateAlpha returns Alpha with metadata`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 15)
        val result = alphaService.calculateAlpha("TGT", "BENCH", from, to)

        assertEquals("TGT", result.target)
        assertEquals("BENCH", result.benchmark)
        assertEquals(from, result.fromDate)
        assertEquals(to, result.toDate)
        assertTrue(result.metadata.dataPoints > 0)
        assertEquals("annualized_excess_return", result.metadata.calculationMethod)
    }

    @Test
    fun `calculateAlpha throws InvalidDateRangeException when fromDate after toDate`() = runBlocking {
        val from = LocalDate(2024, 1, 15)
        val to = LocalDate(2024, 1, 5)
        assertThrows<InvalidDateRangeException> {
            alphaService.calculateAlpha("TGT", "BENCH", from, to)
        }
    }

    @Test
    fun `calculateAlpha throws InvalidDateRangeException when range exceeds maxDays`() = runBlocking {
        val serviceWithMax2 = AlphaServiceImpl(analyticsCache, mockMarketData, maxDays = 2)
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 10)
        assertThrows<InvalidDateRangeException> {
            serviceWithMax2.calculateAlpha("TGT", "BENCH", from, to)
        }
    }
}
