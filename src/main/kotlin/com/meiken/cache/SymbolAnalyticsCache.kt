package com.meiken.cache

import com.meiken.calculator.FinancialCalculations
import com.meiken.data.MarketDataService
import com.meiken.model.DailyPrice
import com.meiken.model.DailyReturn
import com.meiken.observability.Metrics
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val TRADING_DAYS = 252

/**
 * Cached analytics for a symbol over a date range.
 * All values are derived from close-of-day prices only (one price per calendar day per ticker).
 * Contains daily close prices, close-to-close daily returns, volatility (daily and annualized), and return metrics.
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
    val annualizedReturn: Double
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
 * TTL 1 hour, max 1000 entries. Logs cache hits vs misses.
 */
class SymbolAnalyticsCacheService : SymbolAnalyticsCache {

    private val log = LoggerFactory.getLogger(SymbolAnalyticsCacheService::class.java)

    /** Thread-safe cache. No lock needed for get/put; Caffeine handles concurrency. */
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .maximumSize(1000)
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
        cache.getIfPresent(key)?.let { cached ->
            hitsCount.incrementAndGet()
            Metrics.recordCacheHit()
            Metrics.setCacheSize(cache.estimatedSize().toInt())
            Metrics.setCacheHitRate(getHitRate())
            log.info("SymbolAnalytics cache HIT: {}", key)
            return cached
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
            val returnValues = dailyReturns.map { it.returnValue }
            val avgDailyReturn = returnValues.average()
            val (dailyVol, annualizedVol) = FinancialCalculations.calculateVolatility(returnValues, TRADING_DAYS)
            val annualizedReturn = FinancialCalculations.annualizeReturn(avgDailyReturn, TRADING_DAYS)
            val analytics = SymbolAnalytics(
                symbol = symbol,
                fromDate = fromDate,
                toDate = toDate,
                dailyPrices = prices,
                dailyReturns = dailyReturns,
                dailyVolatility = dailyVol,
                annualizedVolatility = annualizedVol,
                averageDailyReturn = avgDailyReturn,
                annualizedReturn = annualizedReturn
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
}
