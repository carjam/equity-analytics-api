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

private const val TRADING_DAYS = 252

/**
 * Uses [SymbolAnalyticsCacheService]: fetches cached [SymbolAnalytics] for target and benchmark in parallel
 * (each symbol/date-range at most one API call; cache hits are instant). Aligns returns by date and computes
 * alpha as annualized target return minus annualized benchmark return. No redundant fetches or calculations.
 */
class AlphaServiceImpl(
    private val analyticsCache: SymbolAnalyticsCacheService,
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365
) : AlphaService {

    /** Computes alpha from cached close-of-day returns for target and benchmark (close-to-close daily returns). */
    override suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
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

        val targetValues = alignedTarget.map { it.returnValue }
        val benchmarkValues = alignedBenchmark.map { it.returnValue }
        val alphaValue = FinancialCalculations.calculateAlpha(targetValues, benchmarkValues, TRADING_DAYS)
        val targetAnnualized = FinancialCalculations.annualizeReturn(targetValues.average(), TRADING_DAYS)
        val benchmarkAnnualized = FinancialCalculations.annualizeReturn(benchmarkValues.average(), TRADING_DAYS)

        Alpha(
            target = target,
            benchmark = benchmark,
            fromDate = fromDate,
            toDate = toDate,
            alpha = alphaValue,
            metadata = AlphaMetadata(
                dataPoints = alignedTarget.size,
                calculationMethod = "annualized_excess_return",
                targetAnnualizedReturn = targetAnnualized,
                benchmarkAnnualizedReturn = benchmarkAnnualized
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
