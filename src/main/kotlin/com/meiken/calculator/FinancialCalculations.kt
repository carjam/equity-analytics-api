package com.meiken.calculator

import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.model.MaxDrawdownResult
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure financial formulas: returns, volatility, alpha, beta, Sharpe, drawdown. All inputs are from close-of-day prices; uses 252 trading days for annualization. */
object FinancialCalculations {

    /** Converts sorted close-of-day prices to daily returns: (curr.close - prev.close) / prev.close per pair of consecutive days. One return per date (close-to-close). */
    fun calculateDailyReturns(prices: List<DailyPrice>): List<DailyReturn> {
        require(prices.size >= 2) { "Need at least 2 prices to calculate returns" }
        return prices.sortedBy { it.date }.zipWithNext { prev, curr ->
            DailyReturn(curr.date, (curr.close - prev.close) / prev.close)
        }
    }

    /** Compounds a scalar daily return to annual: (1 + avgDaily)^tradingDays - 1. Use for single-value quantities like OLS alpha. */
    fun annualizeReturn(avgDailyReturn: Double, tradingDays: Int = 252): Double {
        return (1 + avgDailyReturn).pow(tradingDays.toDouble()) - 1
    }

    /** Geometric-mean annualization: (∏(1+r_t))^(tradingDays/n) - 1. Corrects the arithmetic-mean upward bias of ~σ²/2 per year (Jensen's inequality). */
    fun annualizeReturn(returns: List<Double>, tradingDays: Int = 252): Double {
        require(returns.isNotEmpty()) { "Returns list cannot be empty" }
        val grossProduct = returns.fold(1.0) { acc, r -> acc * (1.0 + r) }
        return grossProduct.pow(tradingDays.toDouble() / returns.size) - 1.0
    }

    /**
     * Jensen's alpha via OLS single-factor regression: (target − rf) = α + β(benchmark − rf) + ε.
     * Returns Pair(annualizedAlpha, beta). Since RF is a constant daily shift it cancels in covariance
     * and variance, so β = cov(target, benchmark) / var(benchmark), then:
     *   α_daily = mean(target) − β × mean(benchmark) − rf_daily × (1 − β)
     * where rf_daily = (1 + riskFreeRate)^(1/tradingDays) − 1. Alpha is then annualized.
     */
    fun calculateAlpha(
        targetReturns: List<Double>,
        benchmarkReturns: List<Double>,
        riskFreeRate: Double = 0.04,
        tradingDays: Int = 252
    ): Pair<Double, Double> {
        require(targetReturns.size == benchmarkReturns.size) { "Must have same number of returns" }
        val benchmarkVar = StatisticalCalculations.variance(benchmarkReturns)
        require(benchmarkVar != 0.0) { "Benchmark variance cannot be zero" }
        val beta = StatisticalCalculations.covariance(targetReturns, benchmarkReturns) / benchmarkVar
        val rfDaily = (1.0 + riskFreeRate).pow(1.0 / tradingDays) - 1.0
        val dailyAlpha = targetReturns.average() - beta * benchmarkReturns.average() - rfDaily * (1.0 - beta)
        return Pair(annualizeReturn(dailyAlpha, tradingDays), beta)
    }

    /** Beta = covariance(target, benchmark) / variance(benchmark). Expects daily returns from close-of-day prices. Fails if benchmark variance is zero. */
    fun calculateBeta(targetReturns: List<Double>, benchmarkReturns: List<Double>): Double {
        val cov = StatisticalCalculations.covariance(targetReturns, benchmarkReturns)
        val benchmarkVar = StatisticalCalculations.variance(benchmarkReturns)
        require(benchmarkVar != 0.0) { "Benchmark variance cannot be zero" }
        return cov / benchmarkVar
    }

    /** Returns (dailyStdDev, annualizedStdDev) of daily returns. Expects close-of-day–based returns. Annualized = daily * sqrt(tradingDays). */
    fun calculateVolatility(returns: List<Double>, tradingDays: Int = 252): Pair<Double, Double> {
        val daily = StatisticalCalculations.standardDeviation(returns)
        return Pair(daily, daily * sqrt(tradingDays.toDouble()))
    }

    /** Sharpe = (annualized return - riskFreeRate) / annualized volatility. Expects daily returns from close-of-day prices. Fails if volatility is zero. */
    fun calculateSharpe(returns: List<Double>, riskFreeRate: Double = 0.04, tradingDays: Int = 252): Double {
        val annualizedReturn = annualizeReturn(returns, tradingDays)
        val (_, annualizedVol) = calculateVolatility(returns, tradingDays)
        require(annualizedVol != 0.0) { "Volatility cannot be zero" }
        return (annualizedReturn - riskFreeRate) / annualizedVol
    }

    /**
     * Sortino Ratio: (annualized return - riskFreeRate) / downside deviation.
     * Like Sharpe ratio, but only penalizes downside volatility (returns below zero).
     * Uses semi-deviation of negative returns only, annualized.
     */
    fun calculateSortino(returns: List<Double>, riskFreeRate: Double = 0.04, tradingDays: Int = 252): Double {
        require(returns.isNotEmpty()) { "Returns list cannot be empty" }
        
        val annualizedReturn = annualizeReturn(returns, tradingDays)
        val downsideReturns = returns.filter { it < 0.0 }
        
        // If no downside returns, downside deviation is zero (perfect upside-only)
        // Return positive infinity would be mathematically correct, but we use a large number
        if (downsideReturns.isEmpty()) {
            return if (annualizedReturn > riskFreeRate) Double.MAX_VALUE / 1e10 else 0.0
        }
        
        // Semi-deviation: sqrt(mean(negative_returns^2))
        val downsideSemiVariance = downsideReturns.map { it * it }.average()
        val dailyDownsideDeviation = sqrt(downsideSemiVariance)
        val annualizedDownsideDeviation = dailyDownsideDeviation * sqrt(tradingDays.toDouble())
        
        require(annualizedDownsideDeviation != 0.0) { "Downside deviation cannot be zero" }
        return (annualizedReturn - riskFreeRate) / annualizedDownsideDeviation
    }

    /**
     * Maximum Drawdown: largest peak-to-trough decline as a percentage.
     * Returns the maximum drawdown value, peak date, trough date, and peak/trough values.
     * MDD = max((peak - trough) / peak) for all peaks in the series.
     */
    fun calculateMaxDrawdown(prices: List<DailyPrice>): MaxDrawdownResult {
        require(prices.isNotEmpty()) { "Cannot calculate max drawdown with empty price list" }
        
        if (prices.size == 1) {
            val price = prices.first()
            return MaxDrawdownResult(
                maxDrawdown = 0.0,
                peakDate = price.date,
                troughDate = price.date,
                peakValue = price.close,
                troughValue = price.close
            )
        }

        var peak = prices.first().close
        var peakDate = prices.first().date
        var maxDD = 0.0
        var maxDDPeakDate = prices.first().date
        var maxDDTroughDate = prices.first().date
        var maxDDPeakValue = prices.first().close
        var maxDDTroughValue = prices.first().close

        prices.forEach { price ->
            if (price.close > peak) {
                peak = price.close
                peakDate = price.date
            }
            val drawdown = (peak - price.close) / peak
            if (drawdown > maxDD) {
                maxDD = drawdown
                maxDDPeakDate = peakDate
                maxDDTroughDate = price.date
                maxDDPeakValue = peak
                maxDDTroughValue = price.close
            }
        }

        return MaxDrawdownResult(
            maxDrawdown = maxDD,
            peakDate = maxDDPeakDate,
            troughDate = maxDDTroughDate,
            peakValue = maxDDPeakValue,
            troughValue = maxDDTroughValue
        )
    }
}
