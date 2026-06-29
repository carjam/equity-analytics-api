package com.meiken.service

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.calculator.FinancialCalculations
import com.meiken.calculator.StatisticalCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.BetaMetadata
import com.meiken.model.BetaResponse
import com.meiken.model.CalmarMetadata
import com.meiken.model.CalmarResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.model.DrawdownData
import com.meiken.model.DrawdownMetadata
import com.meiken.model.DrawdownResponse
import com.meiken.model.InformationRatioMetadata
import com.meiken.model.InformationRatioResponse
import com.meiken.model.MomentumMetadata
import com.meiken.model.MomentumResponse
import com.meiken.model.MovingAverageMetadata
import com.meiken.model.MovingAverageResponse
import com.meiken.model.PriceLevels
import com.meiken.model.PriceLevelsMetadata
import com.meiken.model.PriceLevelsResponse
import com.meiken.model.RelativeStrengthMetadata
import com.meiken.model.RelativeStrengthResponse
import com.meiken.model.RollingCorrelation
import com.meiken.model.SharpeMetadata
import com.meiken.model.SharpeResponse
import com.meiken.model.SortinoMetadata
import com.meiken.model.SortinoResponse
import com.meiken.model.TreynorMetadata
import com.meiken.model.TreynorResponse
import com.meiken.model.VolatilityData
import com.meiken.model.VolatilityMetadata
import com.meiken.model.VolatilityResponse
import com.meiken.model.ZScoreMetadata
import com.meiken.model.ZScoreResponse
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

    /** Calmar from cached data: annualizedReturn / maxDrawdown. Measures return per unit of drawdown risk. */
    override suspend fun calculateCalmar(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): CalmarResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for Calmar ratio" }
        
        val drawdownResult = FinancialCalculations.calculateMaxDrawdown(analytics.dailyPrices)
        val calmar = FinancialCalculations.calculateCalmar(analytics.annualizedReturn, drawdownResult.maxDrawdown)
        val calmarWarnings = analytics.warnings + listOfNotNull(OutputValidator.checkCalmar(calmar))
        
        CalmarResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            calmar = calmar,
            annualizedReturn = analytics.annualizedReturn,
            maxDrawdown = drawdownResult.maxDrawdown,
            metadata = CalmarMetadata(
                dataPoints = analytics.dailyPrices.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = calmarWarnings.ifEmpty { null }
            )
        )
    }

    /** Momentum (Rate of Change) from cached prices: calculates ROC for multiple lookback periods. */
    override suspend fun calculateMomentum(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        lookbacks: List<Int>
    ): MomentumResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        require(lookbacks.isNotEmpty()) { "At least one lookback period is required" }
        require(lookbacks.all { it > 0 }) { "All lookback periods must be positive" }
        
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for momentum calculation" }
        
        val maxLookback = lookbacks.maxOrNull() ?: 0
        require(analytics.dailyPrices.size > maxLookback) {
            "Need more than $maxLookback price points for the requested lookback periods"
        }
        
        // Calculate ROC for all lookback periods and combine
        val allRocData = lookbacks.flatMap { lookback ->
            FinancialCalculations.calculateRateOfChange(analytics.dailyPrices, lookback)
        }
        
        MomentumResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            momentum = allRocData,
            metadata = MomentumMetadata(
                dataPoints = analytics.dailyPrices.size,
                lookbacks = lookbacks,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = analytics.warnings.ifEmpty { null }
            )
        )
    }

    /** Moving Averages from cached prices: calculates SMA for multiple window sizes. */
    override suspend fun calculateMovingAverages(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        windows: List<Int>
    ): MovingAverageResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        require(windows.isNotEmpty()) { "At least one window size is required" }
        require(windows.all { it > 0 }) { "All window sizes must be positive" }
        
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for moving average calculation" }
        
        val maxWindow = windows.maxOrNull() ?: 0
        require(analytics.dailyPrices.size >= maxWindow) {
            "Need at least $maxWindow price points for the requested window sizes"
        }
        
        // Calculate MA for all window sizes and combine
        val allMaData = windows.flatMap { window ->
            FinancialCalculations.calculateMovingAverage(analytics.dailyPrices, window)
        }
        
        MovingAverageResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            movingAverages = allMaData,
            metadata = MovingAverageMetadata(
                dataPoints = analytics.dailyPrices.size,
                windows = windows,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = analytics.warnings.ifEmpty { null }
            )
        )
    }

    /** Price Levels from cached prices: calculates 52-week high/low and distance from current. */
    override suspend fun calculatePriceLevels(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): PriceLevelsResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for price levels calculation" }
        
        val result = FinancialCalculations.calculate52WeekLevels(analytics.dailyPrices)
        
        PriceLevelsResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            levels = PriceLevels(
                current = result.current,
                currentDate = result.currentDate,
                high52Week = result.high52Week,
                high52WeekDate = result.high52WeekDate,
                low52Week = result.low52Week,
                low52WeekDate = result.low52WeekDate,
                distanceFromHigh = result.distanceFromHigh,
                distanceFromLow = result.distanceFromLow
            ),
            metadata = PriceLevelsMetadata(
                dataPoints = analytics.dailyPrices.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = analytics.warnings.ifEmpty { null }
            )
        )
    }

    /** Z-Score from cached prices: number of standard deviations from mean price over window. Mean reversion indicator. */
    override suspend fun calculateZScore(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        window: Int
    ): ZScoreResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        require(window >= 2) { "Window must be at least 2" }
        
        val analytics = analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService)
        require(analytics.dailyPrices.isNotEmpty()) { "No price data available for Z-score calculation" }
        require(analytics.dailyPrices.size >= window) {
            "Need at least $window price points for Z-score calculation"
        }
        
        val zScore = FinancialCalculations.calculateZScore(analytics.dailyPrices, window)
        
        // Calculate mean and std dev for response
        val recentPrices = analytics.dailyPrices.sortedBy { it.date }.takeLast(window)
        val priceValues = recentPrices.map { it.close }
        val mean = priceValues.average()
        val stdDev = StatisticalCalculations.standardDeviation(priceValues)
        
        ZScoreResponse(
            symbol = symbol,
            fromDate = fromDate,
            toDate = toDate,
            zScore = zScore,
            currentPrice = analytics.dailyPrices.sortedBy { it.date }.last().close,
            meanPrice = mean,
            stdDev = stdDev,
            window = window,
            metadata = ZScoreMetadata(
                dataPoints = analytics.dailyPrices.size,
                source = DEFAULT_SOURCE,
                dataQuality = analytics.dataQuality,
                outlierCount = analytics.outlierCount,
                missingDays = analytics.missingDays,
                warnings = analytics.warnings.ifEmpty { null }
            )
        )
    }

    /** Relative Strength from cached prices: relative performance of target vs benchmark. */
    override suspend fun calculateRelativeStrength(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): RelativeStrengthResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        
        val targetAnalytics = async { analyticsCache.getOrCompute(target, fromDate, toDate, marketDataService) }
        val benchmarkAnalytics = async { analyticsCache.getOrCompute(benchmark, fromDate, toDate, marketDataService) }
        
        val targetData = targetAnalytics.await()
        val benchmarkData = benchmarkAnalytics.await()
        
        require(targetData.dailyPrices.isNotEmpty()) { "No price data available for target $target" }
        require(benchmarkData.dailyPrices.isNotEmpty()) { "No price data available for benchmark $benchmark" }
        
        // Align dates
        val targetMap = targetData.dailyPrices.associateBy { it.date }
        val benchmarkMap = benchmarkData.dailyPrices.associateBy { it.date }
        val commonDates = targetMap.keys.intersect(benchmarkMap.keys).sorted()
        
        require(commonDates.isNotEmpty()) { "No common dates between $target and $benchmark" }
        
        val alignedTarget = commonDates.map { targetMap[it]!! }
        val alignedBenchmark = commonDates.map { benchmarkMap[it]!! }
        
        val relativeStrength = FinancialCalculations.calculateRelativeStrength(alignedTarget, alignedBenchmark)
        
        // Calculate individual returns
        val targetReturn = (alignedTarget.last().close / alignedTarget.first().close) - 1.0
        val benchmarkReturn = (alignedBenchmark.last().close / alignedBenchmark.first().close) - 1.0
        
        RelativeStrengthResponse(
            target = target,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            relativeStrength = relativeStrength,
            targetReturn = targetReturn,
            benchmarkReturn = benchmarkReturn,
            metadata = RelativeStrengthMetadata(
                dataPoints = alignedTarget.size,
                source = DEFAULT_SOURCE,
                dataQuality = targetData.dataQuality,
                outlierCount = targetData.outlierCount,
                missingDays = targetData.missingDays,
                warnings = targetData.warnings.ifEmpty { null }
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

    /** Treynor from cached data: (annualizedReturn - riskFreeRate) / beta. Return per unit of systematic risk. */
    override suspend fun calculateTreynor(
        symbol: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        riskFreeRate: Double
    ): TreynorResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val symbolAnalyticsDeferred = async { analyticsCache.getOrCompute(symbol, fromDate, toDate, marketDataService) }
        val benchmarkAnalyticsDeferred = async { analyticsCache.getOrCompute(benchmark, fromDate, toDate, marketDataService) }
        val symbolAnalytics = symbolAnalyticsDeferred.await()
        val benchmarkAnalytics = benchmarkAnalyticsDeferred.await()
        val (alignedSymbol, alignedBenchmark) = alignReturnsByDate(symbolAnalytics.dailyReturns, benchmarkAnalytics.dailyReturns)
        require(alignedSymbol.size >= 2) { "Insufficient overlapping data for Treynor ratio calculation" }
        val symbolValues = alignedSymbol.map { it.returnValue }
        val benchmarkValues = alignedBenchmark.map { it.returnValue }
        val beta = FinancialCalculations.calculateBeta(symbolValues, benchmarkValues)
        require(beta != 0.0) { "Beta is zero; Treynor ratio undefined" }
        val treynor = FinancialCalculations.calculateTreynor(symbolAnalytics.annualizedReturn, riskFreeRate, beta)
        val worstQuality = listOf(symbolAnalytics.dataQuality, benchmarkAnalytics.dataQuality)
            .minByOrNull { when (it) { "POOR" -> 0; "ACCEPTABLE" -> 1; else -> 2 } } ?: "GOOD"
        val warnings = (symbolAnalytics.warnings + benchmarkAnalytics.warnings).distinct() +
            listOfNotNull(OutputValidator.checkTreynor(treynor))
        TreynorResponse(
            symbol = symbol,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            treynor = treynor,
            annualizedReturn = symbolAnalytics.annualizedReturn,
            beta = beta,
            riskFreeRate = riskFreeRate,
            metadata = TreynorMetadata(
                dataPoints = alignedSymbol.size,
                source = DEFAULT_SOURCE,
                dataQuality = worstQuality,
                outlierCount = symbolAnalytics.outlierCount + benchmarkAnalytics.outlierCount,
                missingDays = symbolAnalytics.missingDays + benchmarkAnalytics.missingDays,
                warnings = warnings.ifEmpty { null }
            )
        )
    }

    /** Information ratio from cached data: annualized active return / annualized tracking error. */
    override suspend fun calculateInformationRatio(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): InformationRatioResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        val targetAnalyticsDeferred = async { analyticsCache.getOrCompute(target, fromDate, toDate, marketDataService) }
        val benchmarkAnalyticsDeferred = async { analyticsCache.getOrCompute(benchmark, fromDate, toDate, marketDataService) }
        val targetAnalytics = targetAnalyticsDeferred.await()
        val benchmarkAnalytics = benchmarkAnalyticsDeferred.await()
        val (alignedTarget, alignedBenchmark) = alignReturnsByDate(targetAnalytics.dailyReturns, benchmarkAnalytics.dailyReturns)
        require(alignedTarget.size >= 2) { "Insufficient overlapping data for information ratio calculation" }
        val targetValues = alignedTarget.map { it.returnValue }
        val benchmarkValues = alignedBenchmark.map { it.returnValue }
        val ir = FinancialCalculations.calculateInformationRatio(targetValues, benchmarkValues)
        val activeReturns = targetValues.zip(benchmarkValues).map { (t, b) -> t - b }
        val annualizedActiveReturn = FinancialCalculations.annualizeReturn(activeReturns.average())
        val trackingError = StatisticalCalculations.standardDeviation(activeReturns) * kotlin.math.sqrt(252.0)
        val worstQuality = listOf(targetAnalytics.dataQuality, benchmarkAnalytics.dataQuality)
            .minByOrNull { when (it) { "POOR" -> 0; "ACCEPTABLE" -> 1; else -> 2 } } ?: "GOOD"
        val warnings = (targetAnalytics.warnings + benchmarkAnalytics.warnings).distinct() +
            listOfNotNull(OutputValidator.checkInformationRatio(ir))
        InformationRatioResponse(
            target = target,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            informationRatio = ir,
            annualizedActiveReturn = annualizedActiveReturn,
            trackingError = trackingError,
            metadata = InformationRatioMetadata(
                dataPoints = alignedTarget.size,
                source = DEFAULT_SOURCE,
                dataQuality = worstQuality,
                outlierCount = targetAnalytics.outlierCount + benchmarkAnalytics.outlierCount,
                missingDays = targetAnalytics.missingDays + benchmarkAnalytics.missingDays,
                warnings = warnings.ifEmpty { null }
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
