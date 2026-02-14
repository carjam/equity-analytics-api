package com.meiken.service

import com.meiken.calculator.FinancialCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.Alpha
import com.meiken.model.AlphaMetadata
import com.meiken.model.DailyReturn
import com.meiken.util.validateDateRange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

private const val TRADING_DAYS = 252

/**
 * Fetches target and benchmark prices in parallel, aligns returns by date (same trading days),
 * then computes alpha as annualized target return minus annualized benchmark return.
 */
class AlphaServiceImpl(
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365
) : AlphaService {

    override suspend fun calculateAlpha(
        target: String,
        benchmark: String,
        fromDate: LocalDate,
        toDate: LocalDate
    ): Alpha = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)

        val targetPricesDeferred = async { marketDataService.getHistoricalPrices(target, fromDate, toDate) }
        val benchmarkPricesDeferred = async { marketDataService.getHistoricalPrices(benchmark, fromDate, toDate) }

        val targetPrices = targetPricesDeferred.await()
        val benchmarkPrices = benchmarkPricesDeferred.await()

        val targetReturns = FinancialCalculations.calculateDailyReturns(targetPrices)
        val benchmarkReturns = FinancialCalculations.calculateDailyReturns(benchmarkPrices)

        val (alignedTarget, alignedBenchmark) = alignReturnsByDate(targetReturns, benchmarkReturns)
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
