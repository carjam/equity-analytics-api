package com.meiken.service

import com.meiken.data.MockMarketDataService
import com.meiken.error.InvalidDateRangeException
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReturnsServiceTest {

    private val mockMarketData = MockMarketDataService(kotlin.random.Random(1), 100.0, 0.02)
    private val returnsService = ReturnsServiceImpl(mockMarketData, maxDays = 365, sourceName = "mock")

    @Test
    fun `calculateReturns returns Returns with daily returns`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 10)
        val result = returnsService.calculateReturns("AAPL", from, to)

        assertEquals("AAPL", result.symbol)
        assertEquals(from, result.fromDate)
        assertEquals(to, result.toDate)
        assertEquals(9, result.dailyReturns.size)
        assertEquals(9, result.metadata.dataPoints)
        assertEquals("mock", result.metadata.source)
        assertEquals(LocalDate(2024, 1, 2), result.dailyReturns.first().date)
        assertEquals(LocalDate(2024, 1, 10), result.dailyReturns.last().date)
    }

    @Test
    fun `calculateReturns throws InvalidDateRangeException when fromDate after toDate`() = runBlocking {
        val from = LocalDate(2024, 1, 10)
        val to = LocalDate(2024, 1, 5)
        assertThrows<InvalidDateRangeException> {
            returnsService.calculateReturns("AAPL", from, to)
        }
    }

    @Test
    fun `calculateReturns throws InvalidDateRangeException when range exceeds maxDays`() = runBlocking {
        val serviceWithMax2 = ReturnsServiceImpl(mockMarketData, maxDays = 2, sourceName = "mock")
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 5)
        val ex = assertThrows<InvalidDateRangeException> {
            serviceWithMax2.calculateReturns("AAPL", from, to)
        }
        assertTrue(ex.message!!.contains("365") || ex.message!!.contains("exceed") || ex.message!!.contains("2"))
    }

    @Test
    fun `calculateReturns with single day range fails with insufficient data`() = runBlocking {
        val from = LocalDate(2024, 1, 1)
        val to = LocalDate(2024, 1, 1)
        val ex = assertThrows<IllegalArgumentException> {
            returnsService.calculateReturns("AAPL", from, to)
        }
        assertTrue(ex.message!!.contains("2") || ex.message!!.contains("Need"))
    }
}
