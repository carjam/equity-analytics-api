package com.meiken.service

import com.meiken.cache.SymbolAnalytics
import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.calculator.FinancialCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.Alpha
import com.meiken.model.AlphaMetadata
import com.meiken.model.DailyReturn
import com.meiken.observability.Metrics
import com.meiken.util.validateDateRange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

/**
 * Uses [SymbolAnalyticsCacheService]: fetches cached [SymbolAnalytics] for target and benchmark in parallel
 * (each symbol/date-range at most one API call; cache hits are instant). Aligns returns by date and computes
 * alpha as annualized target return minus annualized benchmark return.
 * [tradingDaysPerYear] from config (meiken.calculations.tradingDaysPerYear) for annualization.
 */
class AlphaServiceImpl(
    private val analyticsCache: SymbolAnalyticsCacheService,
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365,
    private val tradingDaysPerYear: Int = 252
) : AlphaService {

    /** Computes Jensen's alpha via OLS regression of excess returns (target − rf) on (benchmark − rf). */
    override suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        riskFreeRate: Double
    ): Alpha = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)

        val startNanos = System.nanoTime()
        val targetAnalyticsDeferred = async {
            analyticsCache.getOrCompute(target, fromDate, toDate, marketDataService)
        }
        val benchmarkAnalyticsDeferred = async {
            analyticsCache.getOrCompute(benchmark, fromDate, toDate, marketDataService)
        }

        val targetAnalytics = targetAnalyticsDeferred.await()
        val benchmarkAnalytics = benchmarkAnalyticsDeferred.await()
        val parallelDurationSeconds = (System.nanoTime() - startNanos) / 1e9
        Metrics.recordParallelFetchDuration(parallelDurationSeconds)
        Metrics.recordParallelOperationsTotal(2)

        val (alignedTarget, alignedBenchmark) = alignReturnsByDate(
            targetAnalytics.dailyReturns,
            benchmarkAnalytics.dailyReturns
        )
        require(alignedTarget.isNotEmpty()) { "Insufficient overlapping data for alpha calculation" }

        // Use winsorized return values so data errors don't contaminate alpha or annualized returns.
        // Build date → winsorized value maps; fall back to raw returnValue when field is empty.
        val targetCalcMap = targetAnalytics.dailyReturns
            .zip(targetAnalytics.calculationReturnValues)
            .associate { (dr, cv) -> dr.date.toEpochDays() to cv }
        val benchmarkCalcMap = benchmarkAnalytics.dailyReturns
            .zip(benchmarkAnalytics.calculationReturnValues)
            .associate { (dr, cv) -> dr.date.toEpochDays() to cv }
        val targetValues = alignedTarget.map { targetCalcMap[it.date.toEpochDays()] ?: it.returnValue }
        val benchmarkValues = alignedBenchmark.map { benchmarkCalcMap[it.date.toEpochDays()] ?: it.returnValue }
        val (alphaValue, beta) = FinancialCalculations.calculateAlpha(targetValues, benchmarkValues, riskFreeRate, tradingDaysPerYear)
        val targetAnnualized = FinancialCalculations.annualizeReturn(targetValues, tradingDaysPerYear)
        val benchmarkAnnualized = FinancialCalculations.annualizeReturn(benchmarkValues, tradingDaysPerYear)

        val worstQuality = listOf(targetAnalytics.dataQuality, benchmarkAnalytics.dataQuality)
            .minByOrNull { when (it) { "POOR" -> 0; "ACCEPTABLE" -> 1; else -> 2 } } ?: "GOOD"
        val combinedOutliers = targetAnalytics.outlierCount + benchmarkAnalytics.outlierCount
        val combinedMissing = targetAnalytics.missingDays + benchmarkAnalytics.missingDays
        val combinedWarnings = (targetAnalytics.warnings + benchmarkAnalytics.warnings).distinct()
        Alpha(
            target = target,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            alpha = alphaValue,
            metadata = AlphaMetadata(
                dataPoints = alignedTarget.size,
                riskFreeRate = riskFreeRate,
                beta = beta,
                targetAnnualizedReturn = targetAnnualized,
                benchmarkAnnualizedReturn = benchmarkAnnualized,
                dataQuality = worstQuality,
                outlierCount = combinedOutliers,
                missingDays = combinedMissing,
                warnings = combinedWarnings.ifEmpty { null }
            )
        )
    }

    /** Pairs target and benchmark returns by date; only dates present in both series are included. */
    private fun alignReturnsByDate(
        targetReturns: List<DailyReturn>,
        benchmarkReturns: List<DailyReturn>
    ): Pair<List<DailyReturn>, List<DailyReturn>> {
        val benchmarkByDate = benchmarkReturns.associateBy { it.date.toEpochDays() }
        val alignedTarget = mutableListOf<DailyReturn>()
        val alignedBenchmark = mutableListOf<DailyReturn>()
        for (tr in targetReturns) {
            val bench = benchmarkByDate[tr.date.toEpochDays()] ?: continue
            alignedTarget.add(tr)
            alignedBenchmark.add(bench)
        }
        return alignedTarget to alignedBenchmark
    }
}
