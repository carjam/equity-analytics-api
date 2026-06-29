package com.meiken.calculator

import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.model.MaxDrawdownResult
import com.meiken.model.MovingAverageData
import com.meiken.model.PriceLevelsResult
import com.meiken.model.RateOfChangeData
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure financial formulas: returns, volatility, alpha, beta, Sharpe, drawdown, momentum, moving averages, price levels. All inputs are from close-of-day prices; uses 252 trading days for annualization. */
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
     * Calmar Ratio: annualized return / abs(max drawdown).
     * Measures return per unit of maximum drawdown risk.
     * Returns positive infinity if maxDrawdown is 0 and return is positive, 0 if both are 0.
     */
    fun calculateCalmar(annualizedReturn: Double, maxDrawdown: Double): Double {
        require(maxDrawdown >= 0.0) { "Max drawdown must be non-negative" }
        
        if (maxDrawdown == 0.0) {
            return if (annualizedReturn > 0.0) Double.POSITIVE_INFINITY
                   else if (annualizedReturn < 0.0) Double.NEGATIVE_INFINITY
                   else 0.0
        }
        
        return annualizedReturn / maxDrawdown
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

    /**
     * Rate of Change (ROC): percentage change over a lookback period.
     * ROC[t] = (price[t] - price[t-lookback]) / price[t-lookback]
     * Momentum indicator showing price momentum over the lookback window.
     */
    fun calculateRateOfChange(prices: List<DailyPrice>, lookback: Int): List<RateOfChangeData> {
        require(lookback > 0) { "Lookback must be positive" }
        require(prices.size > lookback) { "Need more than $lookback prices for lookback calculation" }
        
        val sortedPrices = prices.sortedBy { it.date }
        val result = mutableListOf<RateOfChangeData>()
        
        for (i in lookback until sortedPrices.size) {
            val currentPrice = sortedPrices[i].close
            val pastPrice = sortedPrices[i - lookback].close
            val roc = (currentPrice - pastPrice) / pastPrice
            
            result.add(RateOfChangeData(
                date = sortedPrices[i].date,
                rateOfChange = roc,
                lookback = lookback
            ))
        }
        
        return result
    }

    /**
     * Simple Moving Average (SMA): average price over a rolling window.
     * MA[t] = mean(price[t-window+1] ... price[t])
     * Smooths price action and identifies trends.
     */
    fun calculateMovingAverage(prices: List<DailyPrice>, window: Int): List<MovingAverageData> {
        require(window > 0) { "Window must be positive" }
        require(prices.size >= window) { "Need at least $window prices for moving average calculation" }
        
        val sortedPrices = prices.sortedBy { it.date }
        val result = mutableListOf<MovingAverageData>()
        
        for (i in (window - 1) until sortedPrices.size) {
            val windowPrices = sortedPrices.subList(i - window + 1, i + 1)
            val average = windowPrices.map { it.close }.average()
            
            result.add(MovingAverageData(
                date = sortedPrices[i].date,
                average = average,
                window = window
            ))
        }
        
        return result
    }

    /**
     * 52-Week High/Low Levels: identifies highest and lowest prices in the last 252 trading days (or all available data if less).
     * Calculates distance from current price to these levels as percentages.
     * Useful for breakout/oversold detection.
     */
    fun calculate52WeekLevels(prices: List<DailyPrice>): PriceLevelsResult {
        require(prices.isNotEmpty()) { "Cannot calculate price levels with empty price list" }
        
        val sortedPrices = prices.sortedBy { it.date }
        val current = sortedPrices.last()
        
        // Use last 252 trading days (52 weeks * 5 days) or all available data
        val lookbackWindow = minOf(252, sortedPrices.size)
        val recentPrices = sortedPrices.takeLast(lookbackWindow)
        
        val highPrice = recentPrices.maxByOrNull { it.close }!!
        val lowPrice = recentPrices.minByOrNull { it.close }!!
        
        val distanceFromHigh = (current.close - highPrice.close) / highPrice.close
        val distanceFromLow = (current.close - lowPrice.close) / lowPrice.close
        
        return PriceLevelsResult(
            current = current.close,
            currentDate = current.date,
            high52Week = highPrice.close,
            high52WeekDate = highPrice.date,
            low52Week = lowPrice.close,
            low52WeekDate = lowPrice.date,
            distanceFromHigh = distanceFromHigh,
            distanceFromLow = distanceFromLow
        )
    }

    /**
     * Z-Score: measures how many standard deviations the current price is from the mean.
     * z = (current_price - mean_price) / std_dev_price
     * Useful for mean reversion strategies: |z| > 2 suggests overbought/oversold conditions.
     */
    fun calculateZScore(prices: List<DailyPrice>, window: Int): Double {
        require(prices.isNotEmpty()) { "Cannot calculate Z-score with empty price list" }
        require(window >= 2) { "Window must be at least 2" }
        require(prices.size >= window) { "Need at least $window prices for Z-score calculation" }
        
        val sortedPrices = prices.sortedBy { it.date }
        val recentPrices = sortedPrices.takeLast(window)
        
        val priceValues = recentPrices.map { it.close }
        val mean = priceValues.average()
        val stdDev = StatisticalCalculations.standardDeviation(priceValues)
        
        require(stdDev != 0.0) { "Standard deviation cannot be zero for Z-score calculation" }
        
        val currentPrice = sortedPrices.last().close
        return (currentPrice - mean) / stdDev
    }

    /**
     * Relative Strength: measures relative performance of target vs benchmark over the period.
     * RS = (target_end / target_start) / (benchmark_end / benchmark_start) - 1
     * Positive values indicate outperformance, negative indicate underperformance.
     */
    fun calculateRelativeStrength(targetPrices: List<DailyPrice>, benchmarkPrices: List<DailyPrice>): Double {
        require(targetPrices.isNotEmpty()) { "Cannot calculate relative strength with empty target prices" }
        require(benchmarkPrices.isNotEmpty()) { "Cannot calculate relative strength with empty benchmark prices" }
        require(targetPrices.size == benchmarkPrices.size) { "Target and benchmark must have same number of prices" }
        
        val sortedTarget = targetPrices.sortedBy { it.date }
        val sortedBenchmark = benchmarkPrices.sortedBy { it.date }
        
        val targetStart = sortedTarget.first().close
        val targetEnd = sortedTarget.last().close
        val benchmarkStart = sortedBenchmark.first().close
        val benchmarkEnd = sortedBenchmark.last().close
        
        require(targetStart > 0.0 && benchmarkStart > 0.0) { "Start prices must be positive" }
        require(benchmarkEnd > 0.0) { "Benchmark end price must be positive" }
        
        val targetReturn = targetEnd / targetStart
        val benchmarkReturn = benchmarkEnd / benchmarkStart
        
        return (targetReturn / benchmarkReturn) - 1.0
    }
}
