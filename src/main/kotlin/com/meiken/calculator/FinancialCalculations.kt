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

    /** Compounds average daily return (from close-of-day returns) to annual: (1 + avgDaily)^tradingDays - 1. */
    fun annualizeReturn(avgDailyReturn: Double, tradingDays: Int = 252): Double {
        return (1 + avgDailyReturn).pow(tradingDays.toDouble()) - 1
    }

    /** Alpha = annualized target return minus annualized benchmark return (excess return). Expects daily returns derived from close-of-day prices. */
    fun calculateAlpha(targetReturns: List<Double>, benchmarkReturns: List<Double>, tradingDays: Int = 252): Double {
        require(targetReturns.size == benchmarkReturns.size) { "Must have same number of returns" }
        val targetAnnualized = annualizeReturn(targetReturns.average(), tradingDays)
        val benchmarkAnnualized = annualizeReturn(benchmarkReturns.average(), tradingDays)
        return targetAnnualized - benchmarkAnnualized
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
        val annualizedReturn = annualizeReturn(returns.average(), tradingDays)
        val (_, annualizedVol) = calculateVolatility(returns, tradingDays)
        require(annualizedVol != 0.0) { "Volatility cannot be zero" }
        return (annualizedReturn - riskFreeRate) / annualizedVol
    }
}
