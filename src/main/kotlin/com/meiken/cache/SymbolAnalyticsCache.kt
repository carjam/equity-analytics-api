package com.meiken.cache

import com.meiken.calculator.FinancialCalculations
import com.meiken.config.CacheConfig
import com.meiken.config.CalculationsConfig
import com.meiken.config.DataQualityConfig
import com.meiken.data.MarketDataService
import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.observability.Metrics
import com.meiken.util.MarketCalendar
import com.meiken.validator.DataValidator
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Cached analytics for a symbol over a date range.
 * All values are derived from close-of-day prices only (one price per calendar day per ticker).
 * Contains daily close prices, close-to-close daily returns, volatility (daily and annualized), and return metrics.
 * Data quality fields: [dataQuality] (GOOD/ACCEPTABLE/POOR), [outlierCount], [missingDays], [dataFreshness], [gapDays], [warnings].
 * Computed once when the cache is populated; Returns, Alpha, and Analytics reuse this data (one API call per symbol/range).
 */
data class SymbolAnalytics(
    val symbol: String,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val dailyPrices: List<DailyPrice>,
    val dailyReturns: List<DailyReturn>,
    val dailyVolatility: Double,
    val annualizedVolatility: Double,
    val averageDailyReturn: Double,
    val annualizedReturn: Double,
    /**
     * Return values after winsorizing outliers at ±outlierSigma. Parallel to [dailyReturns] (same
     * length and order). All metric calculations (volatility, Sharpe, annualized return, alpha) use
     * this series. [dailyReturns] retains the raw values for display. Empty only in legacy/test
     * contexts where the field was not populated.
     */
    val calculationReturnValues: List<Double> = emptyList(),
    /** When this analytics entry was computed/fetched; used for staleness and [dataFreshness]. */
    val fetchedAt: Instant = Clock.System.now(),
    /** Overall data quality: GOOD, ACCEPTABLE, or POOR (from missing days, gaps, outliers). */
    val dataQuality: String = "GOOD",
    /** Number of return outliers (3-sigma) that were winsorized; reported in [warnings] as "winsorized=N". */
    val outlierCount: Int = 0,
    /** Expected trading days in range minus actual price count (from [MarketCalendar.getTradingDays]). */
    val missingDays: Int = 0,
    /** Human-readable freshness: "realtime", "1 hour old", or "stale" (from [fetchedAt] age on serve). */
    val dataFreshness: String = "realtime",
    /** Sample dates from gaps of > [GAP_THRESHOLD] consecutive missing days in range. */
    val gapDays: List<LocalDate> = emptyList(),
    /** Short warning strings (e.g. missing_days=N, outliers=N, sparse_data) for API metadata. */
    val warnings: List<String> = emptyList()
)

/**
 * Abstraction for caching [SymbolAnalytics]. Implementations: in-memory (Caffeine) or Redis (for horizontal scaling).
 */
interface SymbolAnalyticsCache {
    suspend fun getOrCompute(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        marketDataService: MarketDataService
    ): SymbolAnalytics
    fun getEstimatedSize(): Long
    fun getHitRate(): Double
}

/**
 * In-memory cache service for [SymbolAnalytics] (Caffeine). Thread-safe and safe for heavy concurrent use:
 * - Cache hits are lock-free (Caffeine is thread-safe); different keys are served in parallel.
 * - Cache misses are coalesced per key: only one coroutine computes per key; others await that result (no thundering herd).
 * Ensures at most one API call and one computation per symbol/date-range; subsequent requests for the same key
 * are served from cache or from the in-flight computation. Cache key: "${symbol}:${fromDate}:${toDate}".
 * TTL, max size, and data-quality thresholds come from [CacheConfig] and [CalculationsConfig].
 */
class SymbolAnalyticsCacheService(
    private val cacheConfig: CacheConfig,
    private val calculationsConfig: CalculationsConfig,
    private val dataQualityConfig: DataQualityConfig
) : SymbolAnalyticsCache {

    private val log = LoggerFactory.getLogger(SymbolAnalyticsCacheService::class.java)

    /** Thread-safe cache. No lock needed for get/put; Caffeine handles concurrency. */
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(cacheConfig.ttlSeconds, TimeUnit.SECONDS)
        .maximumSize(cacheConfig.maxSize.toLong())
        .build<String, SymbolAnalytics>()

    /** In-flight computations per key. Coalesces concurrent misses for the same key so only one computes. */
    private val inProgress = ConcurrentHashMap<String, CompletableDeferred<SymbolAnalytics>>()

    private val hitsCount = AtomicLong(0)
    private val missesCount = AtomicLong(0)

    /**
     * Returns cached [SymbolAnalytics] if present; otherwise computes once, caches, and returns.
     * Thread-safe and safe for heavy concurrent use:
     * - **Cache hit:** Lock-free read from Caffeine (no mutex); return immediately. Different keys served in parallel.
     * - **Cache miss:** Per-key coalescing via [inProgress]: [putIfAbsent] ensures only one coroutine becomes the
     *   "loader" for this key; others [await] that loader's [CompletableDeferred]. No thundering herd—at most one
     *   API call and one computation per key. Loader puts result in cache, completes the deferred, then removes key
     *   from [inProgress]; on failure, completes exceptionally so all waiters get the same error.
     * All calculations use close-of-day prices. Caller should validate date range before invoking.
     */
    override suspend fun getOrCompute(
        symbol: String,
        fromDate: LocalDate,
        toDate: LocalDate,
        marketDataService: MarketDataService
    ): SymbolAnalytics {
        val key = "$symbol:$fromDate:$toDate"
        // --- Cache HIT: record staleness metric, optionally warn if stale and request includes recent dates, refresh dataFreshness. ---
        cache.getIfPresent(key)?.let { cached ->
            hitsCount.incrementAndGet()
            Metrics.recordCacheHit()
            Metrics.setCacheSize(cache.estimatedSize().toInt())
            Metrics.setCacheHitRate(getHitRate())
            log.info("SymbolAnalytics cache HIT: {}", key)
            val ageSeconds = (Clock.System.now() - cached.fetchedAt).inWholeSeconds
            Metrics.recordDataStalenessSeconds(ageSeconds.toDouble(), symbol)
            if (ageSeconds > cacheConfig.staleSeconds) {
                val today = Clock.System.todayIn(TimeZone.UTC)
                val toDateEpoch = toDate.toEpochDays()
                val todayEpoch = today.toEpochDays()
                if (toDateEpoch >= todayEpoch - cacheConfig.recentDatesDays) {
                    log.warn("SymbolAnalytics cache stale for {}: data is {}s old and request includes recent dates", key, ageSeconds)
                }
            }
            val freshness = when {
                ageSeconds < cacheConfig.oneHourSeconds -> "realtime"
                ageSeconds < cacheConfig.staleSeconds -> "1 hour old"
                else -> "stale"
            }
            return cached.copy(dataFreshness = freshness)
        }
        missesCount.incrementAndGet()
        Metrics.recordCacheMiss()
        val newDeferred = CompletableDeferred<SymbolAnalytics>()
        val existing = inProgress.putIfAbsent(key, newDeferred)
        if (existing != null) {
            return existing.await()
        }
        try {
            log.info("SymbolAnalytics cache MISS: {}", key)
            val prices = marketDataService.getHistoricalPrices(symbol, fromDate, toDate)
            val dailyReturns = FinancialCalculations.calculateDailyReturns(prices)
            require(dailyReturns.size >= 2) { "Need at least 2 returns for analytics (symbol=$symbol)" }
            val rawReturnValues = dailyReturns.map { it.returnValue }
            val tradingDays = calculationsConfig.tradingDaysPerYear
            // Detect outliers for metadata count, then winsorize (cap at ±sigma) before calculations.
            // Winsorization rather than removal: preserves series length and date alignment across
            // securities; neutralizes data errors (stale prices, split artifacts) while keeping the
            // observation in the series. See DataValidator for the full rationale.
            val outlierIndices = DataValidator.detectOutliers(rawReturnValues, dataQualityConfig.outlierSigma)
            Metrics.recordOutliersDetected(outlierIndices.size, symbol)
            val calculationReturnValues = DataValidator.winsorize(rawReturnValues, dataQualityConfig.outlierSigma)
            val avgDailyReturn = calculationReturnValues.average()
            val (dailyVol, annualizedVol) = FinancialCalculations.calculateVolatility(calculationReturnValues, tradingDays)
            val annualizedReturn = FinancialCalculations.annualizeReturn(calculationReturnValues, tradingDays)
            // Data quality: expected trading days (US calendar), missing count, gap runs, quality level, warnings.
            val expectedTradingDays = MarketCalendar.getTradingDays(fromDate, toDate)
            val actualDays = prices.size
            val missingDaysCount = (expectedTradingDays - actualDays).coerceAtLeast(0)
            val gapDaysList = computeGapDays(prices.map { it.date }, fromDate, toDate)
            val dataQualityLevel = computeDataQuality(expectedTradingDays, actualDays, gapDaysList.size, outlierIndices.size)
            val warningsList = buildWarnings(missingDaysCount, gapDaysList, outlierIndices.size, expectedTradingDays, actualDays)
            val fetchedAtNow = Clock.System.now()
            val analytics = SymbolAnalytics(
                symbol = symbol,
                fromDate = fromDate,
                toDate = toDate,
                dailyPrices = prices,
                dailyReturns = dailyReturns,
                calculationReturnValues = calculationReturnValues,
                dailyVolatility = dailyVol,
                annualizedVolatility = annualizedVol,
                averageDailyReturn = avgDailyReturn,
                annualizedReturn = annualizedReturn,
                fetchedAt = fetchedAtNow,
                dataQuality = dataQualityLevel,
                outlierCount = outlierIndices.size,
                missingDays = missingDaysCount,
                dataFreshness = "realtime",
                gapDays = gapDaysList,
                warnings = warningsList
            )
            cache.put(key, analytics)
            Metrics.setCacheSize(cache.estimatedSize().toInt())
            Metrics.setCacheHitRate(getHitRate())
            newDeferred.complete(analytics)
            return analytics
        } catch (e: Throwable) {
            newDeferred.completeExceptionally(e)
            throw e
        } finally {
            inProgress.remove(key)
        }
    }

    /** Current number of entries in the cache (approximate). */
    override fun getEstimatedSize(): Long = cache.estimatedSize()

    /** Hit rate (0.0–1.0) from hits and misses since startup. */
    override fun getHitRate(): Double {
        val h = hitsCount.get()
        val m = missesCount.get()
        val total = h + m
        return if (total == 0L) 0.0 else h.toDouble() / total
    }

    /** Finds runs of consecutive missing days in [fromDate, toDate] longer than [GAP_THRESHOLD]; returns one sample date per gap (start of gap). */
    private fun computeGapDays(priceDates: List<LocalDate>, fromDate: LocalDate, toDate: LocalDate): List<LocalDate> {
        if (priceDates.isEmpty()) return emptyList()
        val sorted = priceDates.sortedBy { it.toEpochDays() }
        val fromEpoch = fromDate.toEpochDays()
        val toEpoch = toDate.toEpochDays()
        val gapSample = mutableListOf<LocalDate>()
        var prevEpoch = fromEpoch - 1
        for (d in sorted) {
            val epoch = d.toEpochDays()
            if (epoch > toEpoch) break
            val run = (epoch - prevEpoch - 1).toInt()
            if (run > cacheConfig.gapThreshold) {
                gapSample.add(LocalDate.fromEpochDays(prevEpoch + 1))
            }
            prevEpoch = epoch
        }
        if (toEpoch - prevEpoch > cacheConfig.gapThreshold) {
            gapSample.add(LocalDate.fromEpochDays(prevEpoch + 1))
        }
        return gapSample
    }

    /** Derives data quality: POOR (sparse, gaps, or many outliers), ACCEPTABLE (some missing/outliers), else GOOD. */
    private fun computeDataQuality(expected: Int, actual: Int, gapCount: Int, outlierCount: Int): String {
        if (expected <= 0) return "GOOD"
        val ratio = actual.toDouble() / expected
        val sparse = ratio < cacheConfig.sparseRatio
        return when {
            sparse || gapCount > 0 || outlierCount > actual / 4 -> "POOR"
            ratio < 0.9 || outlierCount > 0 -> "ACCEPTABLE"
            else -> "GOOD"
        }
    }

    /** Builds list of warning strings for metadata (missing_days, gap_days, outliers, sparse_data). */
    private fun buildWarnings(
        missingDays: Int,
        gapDays: List<LocalDate>,
        outlierCount: Int,
        expected: Int,
        actual: Int
    ): List<String> {
        val w = mutableListOf<String>()
        if (missingDays > 0) w.add("missing_days=$missingDays")
        if (gapDays.isNotEmpty()) w.add("gap_days=${gapDays.size}")
        if (outlierCount > 0) w.add("winsorized=$outlierCount")
        if (expected > 0 && actual.toDouble() / expected < cacheConfig.sparseRatio) w.add("sparse_data")
        return w
    }
}
