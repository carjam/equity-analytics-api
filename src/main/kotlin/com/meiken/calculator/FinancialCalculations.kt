package com.meiken.calculator

import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure financial formulas: returns, volatility, alpha, beta, Sharpe. All inputs are from close-of-day prices; uses 252 trading days for annualization. */
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
}
