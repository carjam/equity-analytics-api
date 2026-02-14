package com.meiken.service

import com.meiken.calculator.FinancialCalculations
import com.meiken.calculator.StatisticalCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.BetaMetadata
import com.meiken.model.BetaResponse
import com.meiken.model.CorrelationResponse
import com.meiken.model.DailyReturn
import com.meiken.model.RollingCorrelation
import com.meiken.model.SharpeMetadata
import com.meiken.model.SharpeResponse
import com.meiken.model.VolatilityData
import com.meiken.model.VolatilityMetadata
import com.meiken.model.VolatilityResponse
import com.meiken.util.validateDateRange
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

private const val TRADING_DAYS = 252
private const val DEFAULT_SOURCE = "market_data"

/**
 * Implements [AnalyticsService] using [MarketDataService] for prices and [FinancialCalculations] /
 * [StatisticalCalculations] for metrics. Date range validated; two-symbol calls fetch in parallel and align by date.
 */
class AnalyticsServiceImpl(
    private val marketDataService: MarketDataService,
    private val maxDays: Int = 365
) : AnalyticsService {

    override suspend fun calculateVolatility(symbol: String, fromDate: LocalDate, toDate: LocalDate): VolatilityResponse =
        coroutineScope {
            validateDateRange(fromDate, toDate, maxDays)
            val prices = marketDataService.getHistoricalPrices(symbol, fromDate, toDate)
            val returns = FinancialCalculations.calculateDailyReturns(prices)
            require(returns.size >= 2) { "Need at least 2 data points for volatility" }
            val values = returns.map { it.returnValue }
            val (daily, annualized) = FinancialCalculations.calculateVolatility(values, TRADING_DAYS)
            VolatilityResponse(
                symbol = symbol,
                fromDate = fromDate,
                toDate = toDate,
                volatility = VolatilityData(daily = daily, annualized = annualized),
                metadata = VolatilityMetadata(dataPoints = returns.size, source = DEFAULT_SOURCE)
            )
        }

    /** Fetches target and benchmark in parallel, aligns returns by date, then beta = covariance / benchmark variance. */
    override suspend fun calculateBeta(target: String, benchmark: String, fromDate: LocalDate, toDate: LocalDate): BetaResponse =
        coroutineScope {
            validateDateRange(fromDate, toDate, maxDays)
            val targetPricesDeferred = async { marketDataService.getHistoricalPrices(target, fromDate, toDate) }
            val benchmarkPricesDeferred = async { marketDataService.getHistoricalPrices(benchmark, fromDate, toDate) }
            val targetPrices = targetPricesDeferred.await()
            val benchmarkPrices = benchmarkPricesDeferred.await()
            val targetReturns = FinancialCalculations.calculateDailyReturns(targetPrices)
            val benchmarkReturns = FinancialCalculations.calculateDailyReturns(benchmarkPrices)
            val (alignedTarget, alignedBenchmark) = alignReturnsByDate(targetReturns, benchmarkReturns)
            require(alignedTarget.size >= 2) { "Insufficient overlapping data for beta calculation" }
            val targetValues = alignedTarget.map { it.returnValue }
            val benchmarkValues = alignedBenchmark.map { it.returnValue }
            val beta = FinancialCalculations.calculateBeta(targetValues, benchmarkValues)
            BetaResponse(
                target = target,
                benchmark = benchmark,
                fromDate = fromDate,
                toDate = toDate,
                beta = beta,
                metadata = BetaMetadata(dataPoints = alignedTarget.size, source = DEFAULT_SOURCE)
            )
        }

    /** Annualized return and volatility from daily returns; Sharpe = (return - riskFreeRate) / annualizedVol. */
    override suspend fun calculateSharpe(symbol: String, fromDate: LocalDate, toDate: LocalDate, riskFreeRate: Double): SharpeResponse =
        coroutineScope {
            validateDateRange(fromDate, toDate, maxDays)
            val prices = marketDataService.getHistoricalPrices(symbol, fromDate, toDate)
            val returns = FinancialCalculations.calculateDailyReturns(prices)
            require(returns.size >= 2) { "Need at least 2 data points for Sharpe ratio" }
            val values = returns.map { it.returnValue }
            val sharpe = FinancialCalculations.calculateSharpe(values, riskFreeRate, TRADING_DAYS)
            SharpeResponse(
                symbol = symbol,
                fromDate = fromDate,
                toDate = toDate,
                sharpe = sharpe,
                riskFreeRate = riskFreeRate,
                metadata = SharpeMetadata(dataPoints = returns.size, source = DEFAULT_SOURCE)
            )
        }

    /** Aligns both series by date, then for each sliding window of [window] days computes Pearson correlation; windows with zero variance are skipped. */
    override suspend fun calculateCorrelation(
        ticker1: String,
        ticker2: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        window: Int
    ): CorrelationResponse = coroutineScope {
        validateDateRange(fromDate, toDate, maxDays)
        require(window >= 2) { "Window must be at least 2" }
        val prices1Deferred = async { marketDataService.getHistoricalPrices(ticker1, fromDate, toDate) }
        val prices2Deferred = async { marketDataService.getHistoricalPrices(ticker2, fromDate, toDate) }
        val prices1 = prices1Deferred.await()
        val prices2 = prices2Deferred.await()
        val returns1 = FinancialCalculations.calculateDailyReturns(prices1)
        val returns2 = FinancialCalculations.calculateDailyReturns(prices2)
        val (aligned1, aligned2) = alignReturnsByDate(returns1, returns2)
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

    /** Returns pairs of returns for dates that appear in both series (same logic as AlphaServiceImpl). */
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
