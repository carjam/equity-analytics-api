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

    @Test
    fun `annualizeReturn with default trading days`() {
        val annualized = FinancialCalculations.annualizeReturn(0.001)
        assertEquals(0.2872, annualized, 0.001)
    }

    @Test
    fun `calculateAlpha with default trading days`() {
        val targetReturns = List(252) { 0.001 }
        val benchmarkReturns = List(252) { 0.0008 }
        val alpha = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns)
        assertEquals(0.0543, alpha, 0.01)
    }

    @Test
    fun `calculateBeta with known values`() {
        // Target and benchmark same -> beta = 1 (covariance = variance when same series)
        val returns = listOf(0.01, 0.02, -0.01, 0.03, 0.0)
        val beta = FinancialCalculations.calculateBeta(returns, returns)
        assertEquals(1.0, beta, 0.0001)
    }

    @Test
    fun `calculateBeta throws when benchmark variance is zero`() {
        val constant = listOf(0.01, 0.01, 0.01)
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateBeta(listOf(0.01, 0.02, 0.03), constant)
        }
    }

    @Test
    fun `calculateBeta throws when series have different lengths`() {
        val targetReturns = listOf(0.01, 0.02)
        val benchmarkReturns = listOf(0.01, 0.02, 0.03)
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateBeta(targetReturns, benchmarkReturns)
        }
    }

    @Test
    fun `calculateVolatility returns daily and annualized`() {
        val returns = listOf(0.01, -0.005, 0.02, 0.0, -0.01)
        val (daily, annualized) = FinancialCalculations.calculateVolatility(returns, 252)
        assert(daily >= 0)
        assert(annualized >= 0)
        assertEquals(annualized, daily * kotlin.math.sqrt(252.0), 0.0001)
    }

    @Test
    fun `calculateVolatility with default trading days`() {
        val returns = listOf(0.01, -0.005, 0.02)
        val (daily, annualized) = FinancialCalculations.calculateVolatility(returns)
        assert(daily >= 0)
        assertEquals(annualized, daily * kotlin.math.sqrt(252.0), 0.0001)
    }

    @Test
    fun `calculateSharpe with positive excess return`() {
        val returns = List(252) { 0.001 }  // ~28.7% annualized
        val sharpe = FinancialCalculations.calculateSharpe(returns, riskFreeRate = 0.04, tradingDays = 252)
        assert(sharpe > 0)
    }

    @Test
    fun `calculateSharpe with default risk free and trading days`() {
        val returns = listOf(0.01, 0.02, -0.005, 0.015, 0.0)
        val sharpe = FinancialCalculations.calculateSharpe(returns)
        // Just ensure it doesn't throw when vol > 0
        assert(!sharpe.isNaN())
    }

    @Test
    fun `calculateSharpe throws when volatility is zero`() {
        val constantReturns = listOf(0.01, 0.01, 0.01)
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateSharpe(constantReturns)
        }
    }
}
