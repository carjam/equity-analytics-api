package com.meiken.service

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.calculator.FinancialCalculations
import com.meiken.calculator.StatisticalCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.BetaMetadata
import com.meiken.model.BetaResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.model.DrawdownData
import com.meiken.model.DrawdownMetadata
import com.meiken.model.DrawdownResponse
import com.meiken.model.RollingCorrelation
import com.meiken.model.SharpeMetadata
import com.meiken.model.SharpeResponse
import com.meiken.model.SortinoMetadata
import com.meiken.model.SortinoResponse
import com.meiken.model.VolatilityData
import com.meiken.model.VolatilityMetadata
import com.meiken.model.VolatilityResponse
import com.meiken.util.validateDateRange
import com.meiken.validator.OutputValidator
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

private const val DEFAULT_SOURCE = "market_data"

/**
 * Implements [AnalyticsService] using [SymbolAnalyticsCacheService] for all metrics. Each symbol/date-range
 * is fetched and computed at most once; volatility, Sharpe, beta, and correlation all reuse cached
 * [SymbolAnalytics] (returns and pre-computed volatility/return). Single-symbol calls use one cache entry;
 * beta/correlation fetch both symbols from cache and compute from their pre-computed returns. Reduces API
 * calls and redundant calculations across all analytics endpoints.
 */
class AnalyticsServiceImpl(
    private val analyticsCache: SymbolAnalyticsCacheService,
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365
) : AnalyticsService {

    /** Volatility (daily and annualized) from cached close-of-day returns (std dev of close-to-close returns). */
    override suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse =
        coroutineScope {
            validateDateRange(fromDate, toDate, maxDays)
            val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
            require(analytics.dailyReturns.size >= 2) { "Need at least 2 data points for volatility" }
            val volWarnings = analytics.warnings +
                listOfNotNull(OutputValidator.checkAnnualizedVolatility(analytics.annualizedVolatility))
            VolatilityResponse(
                symbol = symbol,
                fromDate = fromDate,
                toDate = toDate,
                volatility = VolatilityData(
                    daily = analytics.dailyVolatility,
                    annualized = analytics.annualizedVolatility
                ),
                metadata = VolatilityMetadata(
                    dataPoints = analytics.dailyReturns.size,
                    source = DEFAULT_SOURCE,
                    dataQuality = analytics.dataQuality,
                    outlierCount = analytics.outlierCount,
                    missingDays = analytics.missingDays,
                    warnings = volWarnings.ifEmpty { null }
                )
            )
        }

    /** Beta from cached close-of-day returns for target and benchmark; aligns by date, then covariance / benchmark variance. */
    override suspend fun calculateBeta(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): BetaResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val targetAnalyticsDeferred = async {
            analyticsCache.getOrCompute(target, fromDate, toDate, marketDataService)
        }
        val benchmarkAnalyticsDeferred = async {
            analyticsCache.getOrCompute(benchmark, fromDate, toDate, marketDataService)
        }
        val targetAnalytics = targetAnalyticsDeferred.await()
        val benchmarkAnalytics = benchmarkAnalyticsDeferred.await()
        val (alignedTarget, alignedBenchmark) = alignReturnsByDate(
            targetAnalytics.dailyReturns,
            benchmarkAnalytics.dailyReturns
        )
        require(alignedTarget.size >= 2) { "Insufficient overlapping data for beta calculation" }
        val targetValues = alignedTarget.map { it.returnValue }
        val benchmarkValues = alignedBenchmark.map { it.returnValue }
        val beta = FinancialCalculations.calculateBeta(targetValues, benchmarkValues)
        val worstQuality = listOf(targetAnalytics.dataQuality, benchmarkAnalytics.dataQuality)
            .minByOrNull { when (it) { "POOR" -> 0; "ACCEPTABLE" -> 1; else -> 2 } } ?: "GOOD"
        val betaWarnings = (targetAnalytics.warnings + benchmarkAnalytics.warnings).distinct() +
            listOfNotNull(OutputValidator.checkBeta(beta))
        BetaResponse(
            target = target,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            beta = beta,
            metadata = BetaMetadata(
                dataPoints = alignedTarget.size,
                source = DEFAULT_SOURCE,
                dataQuality = worstQuality,
                outlierCount = targetAnalytics.outlierCount + benchmarkAnalytics.outlierCount,
                missingDays = targetAnalytics.missingDays + benchmarkAnalytics.missingDays,
                warnings = betaWarnings.ifEmpty { null }
            )
        )
    }

    /** Sharpe from cached close-of-day returns: (annualizedReturn - riskFreeRate) / annualizedVolatility. */
    override suspend fun calculateSharpe(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        riskFreeRate: Double
    ): SharpeResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyReturns.size >= 2) { "Need at least 2 data points for Sharpe ratio" }
        require(analytics.annualizedVolatility != 0.0) { "Volatility cannot be zero" }
        val sharpe = (analytics.annualizedReturn - riskFreeRate) / analytics.annualizedVolatility
        val sharpeWarnings = analytics.warnings + listOfNotNull(OutputValidator.checkSharpe(sharpe))
        SharpeResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            sharpe = sharpe,
            riskFreeRate = riskFreeRate,
            metadata = SharpeMetadata(
                dataPoints = analytics.dailyReturns.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = sharpeWarnings.ifEmpty { null }
            )
        )
    }

    /** Sortino from cached close-of-day returns: (annualizedReturn - riskFreeRate) / downsideDeviation. Only penalizes downside volatility. */
    override suspend fun calculateSortino(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        riskFreeRate: Double
    ): SortinoResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyReturns.size >= 2) { "Need at least 2 data points for Sortino ratio" }
        
        val returnValues = if (analytics.calculationReturnValues.isNotEmpty()) {
            analytics.calculationReturnValues
        } else {
            analytics.dailyReturns.map { it.returnValue }
        }
        
        val sortino = FinancialCalculations.calculateSortino(returnValues, riskFreeRate)
        val sortinoWarnings = analytics.warnings + listOfNotNull(OutputValidator.checkSortino(sortino))
        SortinoResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            sortino = sortino,
            riskFreeRate = riskFreeRate,
            metadata = SortinoMetadata(
                dataPoints = analytics.dailyReturns.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = sortinoWarnings.ifEmpty { null }
            )
        )
    }

    /** Rolling correlation from cached close-of-day returns for both tickers; aligns by date, then correlation per window. */
    override suspend fun calculateCorrelation(
        ticker1: String,
        ticker2: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        window: Int
    ): CorrelationResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        require(window >= 2) { "Window must be at least 2" }
        val analytics1Deferred = async {
            analyticsCache.getOrCompute(ticker1, fromDate, toDate, marketDataService)
        }
        val analytics2Deferred = async {
            analyticsCache.getOrCompute(ticker2, fromDate, toDate, marketDataService)
        }
        val analytics1 = analytics1Deferred.await()
        val analytics2 = analytics2Deferred.await()
        val (aligned1, aligned2) = alignReturnsByDate(analytics1.dailyReturns, analytics2.dailyReturns)
        require(aligned1.size >= window) { "Insufficient overlapping data: need at least $window aligned points" }
        val correlations = mutableListOf<RollingCorrelation>()
        for (i in 0..aligned1.size - window) {
            val window1 = aligned1.subList(i, i + window).map { it.returnValue }
            val window2 = aligned2.subList(i, i + window).map { it.returnValue }
            val date = aligned1[i + window - 1].date
            try {
                val corr = StatisticalCalculations.correlation(window1, window2)
                correlations.add(RollingCorrelation(date = date, correlation = corr))
            } catch (_: IllegalArgumentException) {
                // Skip window if variance is zero (correlation undefined)
            }
        }
        CorrelationResponse(
            ticker1 = ticker1,
            ticker2 = ticker2,
            fromDate = fromDate,
            toDate = toDate,
            correlations = correlations
        )
    }

    /** Maximum drawdown from cached close prices: largest peak-to-trough decline as a percentage. */
    override suspend fun calculateDrawdown(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): DrawdownResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for drawdown calculation" }
        
        val result = FinancialCalculations.calculateMaxDrawdown(analytics.dailyPrices)
        val drawdownWarnings = analytics.warnings +
            listOfNotNull(OutputValidator.checkMaxDrawdown(result.maxDrawdown))
        
        DrawdownResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            drawdown = DrawdownData(
                maxDrawdown = result.maxDrawdown,
                peakDate = result.peakDate,
                troughDate = result.troughDate,
                peakValue = result.peakValue,
                troughValue = result.troughValue,
                recoveryDate = findRecoveryDate(analytics.dailyPrices, result.troughDate, result.peakValue)
            ),
            metadata = DrawdownMetadata(
                dataPoints = analytics.dailyPrices.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = drawdownWarnings.ifEmpty { null }
            )
        )
    }

    /** Finds the first date after the trough where price recovers to or above the peak value, or null if no recovery. */
    private fun findRecoveryDate(prices: List<DailyPrice>, troughDate: LocalDate, peakValue: Double): LocalDate? {
        val afterTrough = prices.dropWhile { it.date <= troughDate }
        return afterTrough.firstOrNull { it.close >= peakValue }?.date
    }

    /** Returns pairs of returns for dates that appear in both series. */
    private fun alignReturnsByDate(
        returns1: List<DailyReturn>,
        returns2: List<DailyReturn>
    ): Pair<List<DailyReturn>, List<DailyReturn>> {
        val byDate2 = returns2.associateBy { it.date.toEpochDays() }
        val aligned1 = mutableListOf<DailyReturn>()
        val aligned2 = mutableListOf<DailyReturn>()
        for (r1 in returns1) {
            val r2 = byDate2[r1.date.toEpochDays()] ?: continue
            aligned1.add(r1)
            aligned2.add(r2)
        }
        return aligned1 to aligned2
    }
}
