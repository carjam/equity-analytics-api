package com.meiken.calculator

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FinancialCalculationsTest {

    @Test
    fun `calculateDailyReturns with simple increasing prices`() {
        // Given: Prices increasing from 100 to 110
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0)
        )

        // When: Calculate daily returns
        val returns = FinancialCalculations.calculateDailyReturns(prices)

        // Then: Should have 2 returns (n-1)
        assertEquals(2, returns.size)
        
        // First return: (105 - 100) / 100 = 0.05 (5%)
        assertEquals(LocalDate(2024, 1, 2), returns[0].date)
        assertEquals(0.05, returns[0].returnValue, 0.0001)
        
        // Second return: (110 - 105) / 105 = 0.047619 (4.76%)
        assertEquals(LocalDate(2024, 1, 3), returns[1].date)
        assertEquals(0.047619, returns[1].returnValue, 0.0001)
    }

    @Test
    fun `calculateDailyReturns with price decrease`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 95.0)
        )

        val returns = FinancialCalculations.calculateDailyReturns(prices)

        assertEquals(1, returns.size)
        // (95 - 100) / 100 = -0.05 (-5%)
        assertEquals(-0.05, returns[0].returnValue, 0.0001)
    }

    @Test
    fun `calculateDailyReturns throws exception with insufficient data`() {
        val singlePrice = listOf(DailyPrice(LocalDate(2024, 1, 1), close = 100.0))
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateDailyReturns(singlePrice)
        }
    }

    @Test
    fun `calculateDailyReturns with empty list throws exception`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateDailyReturns(emptyList())
        }
    }

    @Test
    fun `annualizeReturn converts daily to annual return`() {
        // Daily return of 0.1% = 0.001
        val avgDailyReturn = 0.001
        
        // Annualized: (1.001)^252 - 1 ≈ 0.2872 (28.72%)
        val annualized = FinancialCalculations.annualizeReturn(avgDailyReturn, 252)
        
        assertEquals(0.2872, annualized, 0.001)
    }

    @Test
    fun `calculateAlpha with known values`() {
        // Target: consistent 0.1% daily return
        val targetReturns = List(252) { 0.001 }
        
        // Benchmark: consistent 0.08% daily return
        val benchmarkReturns = List(252) { 0.0008 }

        val alpha = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns, 252)

        // Target annualized: ~28.72%
        // Benchmark annualized: ~23.29%
        // Alpha: ~5.43%
        assertEquals(0.0543, alpha, 0.01)
    }

    @Test
    fun `calculateAlpha throws when series have different lengths`() {
        val targetReturns = listOf(0.01, 0.02)
        val benchmarkReturns = listOf(0.01)

        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns)
        }
    }
}
