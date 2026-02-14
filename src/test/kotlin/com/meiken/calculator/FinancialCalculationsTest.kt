package com.meiken.calculator

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class FinancialCalculationsTest {

    @Test
    fun \`calculateDailyReturns with simple increasing prices\`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0)
        )

        val returns = FinancialCalculations.calculateDailyReturns(prices)

        assertEquals(2, returns.size)
        assertEquals(0.05, returns[0].returnValue, 0.0001)
        assertEquals(0.047619, returns[1].returnValue, 0.0001)
    }

    @Test
    fun \`calculateDailyReturns with price decrease\`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 95.0)
        )

        val returns = FinancialCalculations.calculateDailyReturns(prices)
        assertEquals(-0.05, returns[0].returnValue, 0.0001)
    }

    @Test
    fun \`calculateDailyReturns throws with insufficient data\`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateDailyReturns(listOf(DailyPrice(LocalDate(2024, 1, 1), 100.0)))
        }
    }

    @Test
    fun \`annualizeReturn converts daily to annual\`() {
        val annualized = FinancialCalculations.annualizeReturn(0.001, 252)
        assertEquals(0.2872, annualized, 0.001)
    }

    @Test
    fun \`calculateAlpha with known values\`() {
        val targetReturns = List(252) { 0.001 }
        val benchmarkReturns = List(252) { 0.0008 }
        val alpha = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns, 252)
        assertEquals(0.0543, alpha, 0.01)
    }

    @Test
    fun \`calculateBeta with correlated movements\`() {
        val target = listOf(0.02, 0.03, -0.01, 0.04)
        val benchmark = listOf(0.01, 0.02, -0.005, 0.03)
        val beta = FinancialCalculations.calculateBeta(target, benchmark)
        assertEquals(1.0, beta, 0.3)
    }

    @Test
    fun \`calculateVolatility returns daily and annualized\`() {
        val returns = listOf(0.01, -0.005, 0.02, -0.01, 0.015)
        val (daily, annualized) = FinancialCalculations.calculateVolatility(returns)
        assert(annualized > daily)
    }

    @Test
    fun \`calculateSharpe with positive returns\`() {
        val returns = List(252) { 0.002 }
        val sharpe = FinancialCalculations.calculateSharpe(returns, 0.04)
        assert(sharpe > 0)
    }
}
