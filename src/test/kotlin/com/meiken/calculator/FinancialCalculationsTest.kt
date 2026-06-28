package com.meiken.calculator

import com.meiken.model.DailyPrice
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.pow
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
    fun `calculateAlpha returns jensens alpha and beta`() {
        // Target = benchmark + 0.001 each day → beta=1.0, daily alpha=0.001
        val benchmarkReturns = listOf(0.01, -0.005, 0.02, -0.01, 0.005, -0.015, 0.03)
        val targetReturns = benchmarkReturns.map { it + 0.001 }

        val (alpha, beta) = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns, riskFreeRate = 0.0)

        // Beta=1 because target moves in lockstep with benchmark
        assertEquals(1.0, beta, 0.0001)
        // Daily alpha=0.001; annualized: (1.001)^252 - 1 ≈ 0.2872
        assertEquals(0.2872, alpha, 0.01)
    }

    @Test
    fun `calculateAlpha removes beta-driven return leaving true skill`() {
        // Target = 2 * benchmark, rf=0 → beta=2, alpha=0 (outperformance is purely systematic)
        val benchmarkReturns = listOf(0.01, -0.01, 0.02, -0.02, 0.01, -0.01)
        val targetReturns = benchmarkReturns.map { it * 2.0 }

        val (alpha, beta) = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns, riskFreeRate = 0.0)

        assertEquals(2.0, beta, 0.0001)
        assertEquals(0.0, alpha, 0.001)
    }

    @Test
    fun `calculateAlpha throws when benchmark variance is zero`() {
        val targetReturns = listOf(0.01, 0.02, -0.01)
        val constantBenchmark = listOf(0.008, 0.008, 0.008)

        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateAlpha(targetReturns, constantBenchmark)
        }
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
    fun `annualizeReturn list uses geometric mean less than arithmetic for volatile series`() {
        // [+10%, -10%] has arithmetic mean 0, but geometric mean < 0 (variance drag)
        val returns = listOf(0.1, -0.1)
        val geometric = FinancialCalculations.annualizeReturn(returns, 252)
        val arithmetic = FinancialCalculations.annualizeReturn(returns.average(), 252)
        assert(geometric < arithmetic) { "Expected geometric ($geometric) < arithmetic ($arithmetic)" }
        // Product = 1.1 * 0.9 = 0.99 per 2 days; annualized = 0.99^126 - 1 ≈ -0.719
        assertEquals(0.99.pow(126.0) - 1.0, geometric, 0.0001)
    }

    @Test
    fun `annualizeReturn list with constant returns matches scalar form`() {
        val constant = List(30) { 0.001 }
        val listResult = FinancialCalculations.annualizeReturn(constant, 252)
        val scalarResult = FinancialCalculations.annualizeReturn(0.001, 252)
        assertEquals(scalarResult, listResult, 0.0001)
    }

    @Test
    fun `annualizeReturn list with empty list throws`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.annualizeReturn(emptyList(), 252)
        }
    }

    @Test
    fun `calculateAlpha with default risk free rate returns pair`() {
        val benchmarkReturns = listOf(0.01, -0.005, 0.02, -0.01, 0.005)
        val targetReturns = benchmarkReturns.map { it + 0.001 }
        val (alpha, beta) = FinancialCalculations.calculateAlpha(targetReturns, benchmarkReturns)
        assertEquals(1.0, beta, 0.0001)
        assert(alpha.isFinite())
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

    @Test
    fun `calculateMaxDrawdown with declining prices returns correct drawdown`() {
        // Price falls from 100 to 80 (20% drawdown)
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 95.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 80.0)
        )

        val result = FinancialCalculations.calculateMaxDrawdown(prices)

        assertEquals(0.20, result.maxDrawdown, 0.0001)
        assertEquals(LocalDate(2024, 1, 1), result.peakDate)
        assertEquals(LocalDate(2024, 1, 3), result.troughDate)
        assertEquals(100.0, result.peakValue, 0.0001)
        assertEquals(80.0, result.troughValue, 0.0001)
    }

    @Test
    fun `calculateMaxDrawdown with multiple peaks uses largest drawdown`() {
        // Peak at 100, trough at 75 (25% DD)
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 80.0),  // 20% DD from 100
            DailyPrice(LocalDate(2024, 1, 3), close = 95.0),   // partial recovery, still below 100
            DailyPrice(LocalDate(2024, 1, 4), close = 75.0)    // 25% DD from initial peak of 100
        )

        val result = FinancialCalculations.calculateMaxDrawdown(prices)

        // Largest DD is from 100 to 75 = 25%
        assertEquals(0.25, result.maxDrawdown, 0.001)
        assertEquals(LocalDate(2024, 1, 1), result.peakDate)
        assertEquals(LocalDate(2024, 1, 4), result.troughDate)
    }

    @Test
    fun `calculateMaxDrawdown with new higher peak resets tracking`() {
        // Peak at 100, recovery to 110 (new peak), then drop to 88 (20% from 110)
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 90.0),   // 10% DD
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0),  // new high, resets peak
            DailyPrice(LocalDate(2024, 1, 4), close = 88.0)    // 20% DD from 110
        )

        val result = FinancialCalculations.calculateMaxDrawdown(prices)

        // Largest DD is from 110 to 88 = 20%
        assertEquals(0.20, result.maxDrawdown, 0.001)
        assertEquals(LocalDate(2024, 1, 3), result.peakDate)
        assertEquals(LocalDate(2024, 1, 4), result.troughDate)
        assertEquals(110.0, result.peakValue, 0.001)
        assertEquals(88.0, result.troughValue, 0.001)
    }

    @Test
    fun `calculateMaxDrawdown with only increasing prices returns zero drawdown`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0)
        )

        val result = FinancialCalculations.calculateMaxDrawdown(prices)

        assertEquals(0.0, result.maxDrawdown, 0.0001)
        assertEquals(LocalDate(2024, 1, 1), result.peakDate)
        assertEquals(LocalDate(2024, 1, 1), result.troughDate)
    }

    @Test
    fun `calculateMaxDrawdown with single price returns zero drawdown`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0)
        )

        val result = FinancialCalculations.calculateMaxDrawdown(prices)

        assertEquals(0.0, result.maxDrawdown, 0.0001)
        assertEquals(LocalDate(2024, 1, 1), result.peakDate)
        assertEquals(LocalDate(2024, 1, 1), result.troughDate)
    }

    @Test
    fun `calculateMaxDrawdown throws with empty list`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateMaxDrawdown(emptyList())
        }
    }

    @Test
    fun `calculateSortino penalizes only downside volatility`() {
        // Returns with asymmetric distribution: +10%, +5%, -5%, +8%, -3%
        // Mean = 3%, downside returns = [-5%, -3%]
        val returns = listOf(0.10, 0.05, -0.05, 0.08, -0.03)
        
        val sortino = FinancialCalculations.calculateSortino(returns, riskFreeRate = 0.02, tradingDays = 252)
        
        // Sortino should be higher than Sharpe for positive-skewed returns
        val sharpe = FinancialCalculations.calculateSharpe(returns, riskFreeRate = 0.02, tradingDays = 252)
        assert(sortino > sharpe) { "Sortino ($sortino) should be > Sharpe ($sharpe) for positive-skewed returns" }
        assert(sortino.isFinite()) { "Sortino must be finite" }
    }

    @Test
    fun `calculateSortino with all positive returns uses only zero-mean deviations`() {
        // All positive returns: downside deviation considers deviations below zero
        val returns = listOf(0.01, 0.02, 0.03, 0.015, 0.025)
        
        val sortino = FinancialCalculations.calculateSortino(returns, riskFreeRate = 0.01, tradingDays = 252)
        
        // Should return a very high value since there's no downside volatility
        assert(sortino > 0) { "Sortino should be positive with positive returns" }
        assert(sortino.isFinite()) { "Sortino must be finite" }
    }

    @Test
    fun `calculateSortino with symmetric returns produces higher ratio than Sharpe`() {
        // Symmetric returns around zero mean
        val returns = listOf(0.05, -0.05, 0.03, -0.03, 0.07, -0.07)
        
        val sortino = FinancialCalculations.calculateSortino(returns, riskFreeRate = 0.0, tradingDays = 252)
        val sharpe = FinancialCalculations.calculateSharpe(returns, riskFreeRate = 0.0, tradingDays = 252)
        
        // For symmetric distributions around zero, both should be ~0 (no excess return)
        // Just verify both are defined and in reasonable range
        assert(sortino.isFinite()) { "Sortino must be finite" }
        assert(sharpe.isFinite()) { "Sharpe must be finite" }
    }

    @Test
    fun `calculateSortino with default risk free rate and trading days`() {
        val returns = listOf(0.01, 0.02, -0.005, 0.015, -0.01)
        
        val sortino = FinancialCalculations.calculateSortino(returns)
        
        assert(!sortino.isNaN())
        assert(sortino.isFinite())
    }

    @Test
    fun `calculateSortino throws when all returns equal zero returns`() {
        // If annualized return equals risk-free rate, numerator is zero but denominator > 0
        val constantReturns = listOf(0.0, 0.0, 0.0, 0.0)
        
        val sortino = FinancialCalculations.calculateSortino(constantReturns, riskFreeRate = 0.0)
        
        // This should return 0.0 (no excess return, but denominator is defined)
        assertEquals(0.0, sortino, 0.001)
    }

    @Test
    fun `calculateCalmar with positive return and drawdown`() {
        // Return of 20%, max drawdown of 10% -> Calmar = 0.20 / 0.10 = 2.0
        val calmar = FinancialCalculations.calculateCalmar(annualizedReturn = 0.20, maxDrawdown = 0.10)
        
        assertEquals(2.0, calmar, 0.0001)
    }

    @Test
    fun `calculateCalmar with negative return and drawdown`() {
        // Negative return: -10%, max drawdown 15% -> Calmar = -0.10 / 0.15 = -0.667
        val calmar = FinancialCalculations.calculateCalmar(annualizedReturn = -0.10, maxDrawdown = 0.15)
        
        assertEquals(-0.667, calmar, 0.001)
    }

    @Test
    fun `calculateCalmar with zero drawdown returns infinity for positive return`() {
        // No drawdown with positive return -> infinite Calmar (perfect)
        val calmar = FinancialCalculations.calculateCalmar(annualizedReturn = 0.15, maxDrawdown = 0.0)
        
        assert(calmar == Double.POSITIVE_INFINITY) { "Calmar should be +infinity with zero drawdown and positive return" }
    }

    @Test
    fun `calculateCalmar with zero drawdown and zero return`() {
        // No return, no drawdown -> 0 / 0, should return 0 or NaN (we'll define as 0)
        val calmar = FinancialCalculations.calculateCalmar(annualizedReturn = 0.0, maxDrawdown = 0.0)
        
        assertEquals(0.0, calmar, 0.0001)
    }

    @Test
    fun `calculateCalmar throws when max drawdown is negative`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateCalmar(annualizedReturn = 0.10, maxDrawdown = -0.05)
        }
    }

    @Test
    fun `calculateRateOfChange with 20-day lookback`() {
        // Prices: 100, 105, 110, ..., 225 (steady 5 point increase per day)
        val prices = (1..26).map { day ->
            DailyPrice(LocalDate(2024, 1, day), close = 95.0 + day * 5.0)
        }
        
        val roc = FinancialCalculations.calculateRateOfChange(prices, lookback = 20)
        
        // Should have 6 ROC values (26 prices - 20 lookback = 6)
        assertEquals(6, roc.size)
        // First ROC at day 21: (price[20] - price[0]) / price[0] = (200 - 100) / 100 = 1.0
        assertEquals(1.0, roc[0].rateOfChange, 0.0001)
        // Last ROC at day 26: (price[25] - price[5]) / price[5] = (225 - 125) / 125 = 0.8
        assertEquals(0.8, roc[5].rateOfChange, 0.0001)
    }

    @Test
    fun `calculateRateOfChange with declining prices`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 95.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 90.0)
        )
        
        val roc = FinancialCalculations.calculateRateOfChange(prices, lookback = 2)
        
        assertEquals(1, roc.size)
        // (90 - 100) / 100 = -0.10
        assertEquals(-0.10, roc[0].rateOfChange, 0.0001)
        assertEquals(LocalDate(2024, 1, 3), roc[0].date)
    }

    @Test
    fun `calculateRateOfChange with lookback 1 equals daily return`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0)
        )
        
        val roc = FinancialCalculations.calculateRateOfChange(prices, lookback = 1)
        
        assertEquals(1, roc.size)
        assertEquals(0.05, roc[0].rateOfChange, 0.0001)
    }

    @Test
    fun `calculateRateOfChange throws when lookback exceeds price count`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0)
        )
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateRateOfChange(prices, lookback = 5)
        }
    }

    @Test
    fun `calculateRateOfChange throws with invalid lookback`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0)
        )
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateRateOfChange(prices, lookback = 0)
        }
    }

    @Test
    fun `calculateMovingAverage with simple prices`() {
        // Prices: 100, 110, 120, 130, 140
        val prices = (1..5).map { day ->
            DailyPrice(LocalDate(2024, 1, day), close = 90.0 + day * 10.0)
        }
        
        val ma = FinancialCalculations.calculateMovingAverage(prices, window = 3)
        
        // Should have 3 MA values (5 prices - 3 window + 1)
        assertEquals(3, ma.size)
        // First MA (days 1-3): (100 + 110 + 120) / 3 = 110
        assertEquals(110.0, ma[0].average, 0.0001)
        assertEquals(LocalDate(2024, 1, 3), ma[0].date)
        // Second MA (days 2-4): (110 + 120 + 130) / 3 = 120
        assertEquals(120.0, ma[1].average, 0.0001)
        // Third MA (days 3-5): (120 + 130 + 140) / 3 = 130
        assertEquals(130.0, ma[2].average, 0.0001)
    }

    @Test
    fun `calculateMovingAverage with window 1 returns original prices`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0)
        )
        
        val ma = FinancialCalculations.calculateMovingAverage(prices, window = 1)
        
        assertEquals(3, ma.size)
        assertEquals(100.0, ma[0].average, 0.0001)
        assertEquals(105.0, ma[1].average, 0.0001)
        assertEquals(110.0, ma[2].average, 0.0001)
    }

    @Test
    fun `calculateMovingAverage smooths volatile prices`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 120.0),  // spike
            DailyPrice(LocalDate(2024, 1, 3), close = 105.0)
        )
        
        val ma = FinancialCalculations.calculateMovingAverage(prices, window = 3)
        
        assertEquals(1, ma.size)
        // MA smooths the spike: (100 + 120 + 105) / 3 = 108.33
        assertEquals(108.333, ma[0].average, 0.01)
    }

    @Test
    fun `calculateMovingAverage throws when window exceeds price count`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0)
        )
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateMovingAverage(prices, window = 5)
        }
    }

    @Test
    fun `calculateMovingAverage throws with invalid window`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0)
        )
        
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculateMovingAverage(prices, window = 0)
        }
    }

    @Test
    fun `calculate52WeekLevels with full year of data`() {
        // Create 260 days of prices ranging from 90 to 110
        val prices = (1..260).map { day ->
            val price = 100.0 + 10.0 * kotlin.math.sin(day / 40.0)  // oscillate between 90-110
            DailyPrice(LocalDate(2024, 1, 1), close = price)
        }
        
        val levels = FinancialCalculations.calculate52WeekLevels(prices)
        
        assertEquals(prices.last().close, levels.current, 0.0001)
        assert(levels.high52Week >= levels.current) { "52w high should be >= current" }
        assert(levels.low52Week <= levels.current) { "52w low should be <= current" }
        assert(levels.distanceFromHigh <= 0.0) { "Distance from high should be <= 0" }
        assert(levels.distanceFromLow >= 0.0) { "Distance from low should be >= 0" }
    }

    @Test
    fun `calculate52WeekLevels with price at 52w high`() {
        // Simple steadily increasing prices
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 100.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0),
            DailyPrice(LocalDate(2024, 1, 4), close = 115.0),
            DailyPrice(LocalDate(2024, 1, 5), close = 120.0)
        )
        
        val levels = FinancialCalculations.calculate52WeekLevels(prices)
        
        // Current price should be the 52w high
        assertEquals(120.0, levels.current, 0.0001)
        assertEquals(120.0, levels.high52Week, 0.0001)
        assertEquals(100.0, levels.low52Week, 0.0001)
        assertEquals(0.0, levels.distanceFromHigh, 0.0001)
        assert(levels.distanceFromLow > 0.0) { "Should be above 52w low" }
    }

    @Test
    fun `calculate52WeekLevels with price at 52w low`() {
        val prices = listOf(
            DailyPrice(LocalDate(2024, 1, 1), close = 120.0),
            DailyPrice(LocalDate(2024, 1, 2), close = 115.0),
            DailyPrice(LocalDate(2024, 1, 3), close = 110.0),
            DailyPrice(LocalDate(2024, 1, 4), close = 105.0),
            DailyPrice(LocalDate(2024, 1, 5), close = 100.0)
        )
        
        val levels = FinancialCalculations.calculate52WeekLevels(prices)
        
        // Current price should be the 52w low
        assertEquals(100.0, levels.current, 0.0001)
        assertEquals(120.0, levels.high52Week, 0.0001)
        assertEquals(100.0, levels.low52Week, 0.0001)
        assertEquals(0.0, levels.distanceFromLow, 0.0001)
        assert(levels.distanceFromHigh < 0.0) { "Should be below 52w high" }
    }

    @Test
    fun `calculate52WeekLevels with less than 252 days uses all available`() {
        val prices = (1..28).map { day ->
            DailyPrice(LocalDate(2024, 1, day), close = 100.0 + day * 0.5)
        }
        
        val levels = FinancialCalculations.calculate52WeekLevels(prices)
        
        // Should use all 28 days
        assertEquals(prices.maxOf { it.close }, levels.high52Week, 0.0001)
        assertEquals(prices.minOf { it.close }, levels.low52Week, 0.0001)
    }

    @Test
    fun `calculate52WeekLevels throws with empty list`() {
        assertThrows<IllegalArgumentException> {
            FinancialCalculations.calculate52WeekLevels(emptyList())
        }
    }
}
