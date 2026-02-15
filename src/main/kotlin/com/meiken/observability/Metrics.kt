package com.meiken.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Central metric definitions for production observability.
 * Call [init] once at startup with the application's [MeterRegistry] (e.g. Prometheus).
 * All metrics use consistent names and labels for Prometheus/Grafana.
 */
object Metrics {

    private var registry: MeterRegistry? = null

    private val inFlight = AtomicInteger(0)

    // Cache: gauges backed by atomic holders (updated by cache)
    private val cacheSizeHolder = AtomicInteger(0)
    private val cacheHitRateHolder = AtomicInteger(0) // 0–1000 = 0.000–1.000

    private var inFlightRequestsGauge: Gauge? = null
    private var cacheSizeGauge: Gauge? = null
    private var cacheHitRateGauge: Gauge? = null

    /**
     * Initializes all metrics with the given [MeterRegistry]. Must be called once at startup.
     */
    fun init(meterRegistry: MeterRegistry) {
        registry = meterRegistry

        inFlightRequestsGauge = Gauge.builder("in_flight_requests", inFlight) { it.get().toDouble() }
            .description("Number of requests currently being processed")
            .tag("app", "meiken")
            .register(meterRegistry)

        cacheSizeGauge = Gauge.builder("cache_size", cacheSizeHolder) { it.get().toDouble() }
            .description("Current number of entries in symbol analytics cache")
            .tag("app", "meiken")
            .register(meterRegistry)

        cacheHitRateGauge = Gauge.builder("cache_hit_rate", cacheHitRateHolder) { it.get() / 1000.0 }
            .description("Cache hit rate (0.0–1.0)")
            .tag("app", "meiken")
            .register(meterRegistry)
    }

    // --- API: api_requests_total (endpoint, status, method), api_request_duration_seconds (endpoint), in_flight_requests ---

    fun recordApiRequest(endpoint: String, status: String, method: String, durationSeconds: Double) {
        val r = registry ?: return
        Counter.builder("api_requests_total")
            .description("Total API requests")
            .tag("app", "meiken")
            .tag("endpoint", endpoint)
            .tag("status", status)
            .tag("method", method)
            .register(r)
            .increment()
        Timer.builder("api_request_duration_seconds")
            .description("API request duration in seconds")
            .tag("app", "meiken")
            .tag("endpoint", endpoint)
            .register(r)
            .record((durationSeconds * 1_000).toLong(), TimeUnit.MILLISECONDS)
    }

    fun incrementInFlight() = inFlight.incrementAndGet()
    fun decrementInFlight() = inFlight.decrementAndGet()

    // --- Cache: cache_hits_total, cache_misses_total, cache_size, cache_hit_rate ---

    fun recordCacheHit() {
        registry?.let { r ->
            Counter.builder("cache_hits_total")
                .description("Total symbol analytics cache hits")
                .tag("app", "meiken")
                .register(r)
                .increment()
        }
    }

    fun recordCacheMiss() {
        registry?.let { r ->
            Counter.builder("cache_misses_total")
                .description("Total symbol analytics cache misses")
                .tag("app", "meiken")
                .register(r)
                .increment()
        }
    }

    fun setCacheSize(size: Int) = cacheSizeHolder.set(size)
    fun setCacheHitRate(rate: Double) = cacheHitRateHolder.set((rate.coerceIn(0.0, 1.0) * 1000).toInt())

    // --- Alpha Vantage: alpha_vantage_calls_total (symbol, status), alpha_vantage_request_duration_seconds ---

    fun recordAlphaVantageCall(symbol: String, status: String) {
        registry?.let { r ->
            Counter.builder("alpha_vantage_calls_total")
                .description("Total Alpha Vantage API calls")
                .tag("app", "meiken")
                .tag("symbol", symbol)
                .tag("status", status)
                .register(r)
                .increment()
        }
    }

    fun recordAlphaVantageDuration(seconds: Double) {
        registry?.let { r ->
            Timer.builder("alpha_vantage_request_duration_seconds")
                .description("Alpha Vantage API request duration in seconds")
                .tag("app", "meiken")
                .register(r)
                .record((seconds * 1_000).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    // --- API key auth: api_key_usage_total (key_id, endpoint), api_key_authentication_failures_total ---

    fun recordApiKeyUsage(keyId: String, endpoint: String) {
        registry?.let { r ->
            Counter.builder("api_key_usage_total")
                .description("Total API key usage by key and endpoint")
                .tag("app", "meiken")
                .tag("key_id", keyId)
                .tag("endpoint", endpoint)
                .register(r)
                .increment()
        }
    }

    fun recordApiKeyAuthFailure() {
        registry?.let { r ->
            Counter.builder("api_key_authentication_failures_total")
                .description("Total API key authentication failures")
                .tag("app", "meiken")
                .register(r)
                .increment()
        }
    }

    fun getRegistry(): MeterRegistry? = registry

    // --- Resilience: circuit_breaker_state, circuit_breaker_calls_total, retry_attempts_total, request_timeout_total, graceful_shutdown_duration_seconds ---

    fun recordCircuitBreakerState(state: String) {
        registry?.let { r ->
            Gauge.builder("circuit_breaker_state", { stateToOrdinal(state).toDouble() })
                .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .tag("app", "meiken")
                .tag("name", "alphaVantage")
                .tag("state", state)
                .register(r)
        }
    }

    fun recordCircuitBreakerCall(state: String, kind: String) {
        registry?.let { r ->
            Counter.builder("circuit_breaker_calls_total")
                .description("Circuit breaker calls by state and kind")
                .tag("app", "meiken")
                .tag("name", "alphaVantage")
                .tag("state", state)
                .tag("kind", kind)
                .register(r)
                .increment()
        }
    }

    fun registerCircuitBreakerGauge(name: String, valueSupplier: () -> Double) {
        registry?.let { r ->
            Gauge.builder("circuit_breaker_state_gauge", valueSupplier)
                .description("Circuit breaker state gauge: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .tag("app", "meiken")
                .tag("name", name)
                .register(r)
        }
    }

    fun recordRetryAttempt(outcome: String) {
        registry?.let { r ->
            Counter.builder("retry_attempts_total")
                .description("Retry attempts by outcome")
                .tag("app", "meiken")
                .tag("outcome", outcome)
                .register(r)
                .increment()
        }
    }

    fun recordRequestTimeout() {
        registry?.let { r ->
            Counter.builder("request_timeout_total")
                .description("Total request timeouts")
                .tag("app", "meiken")
                .register(r)
                .increment()
        }
    }

    fun recordGracefulShutdownDuration(seconds: Double) {
        registry?.let { r ->
            Timer.builder("graceful_shutdown_duration_seconds")
                .description("Graceful shutdown duration in seconds")
                .tag("app", "meiken")
                .register(r)
                .record((seconds * 1_000).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    private fun stateToOrdinal(state: String): Int = when (state.uppercase()) {
        "CLOSED" -> 0
        "OPEN" -> 1
        "HALF_OPEN" -> 2
        else -> -1
    }

    // --- Performance: parallel_fetch_duration_seconds, response_size_bytes, parallel_operations_total ---

    fun recordParallelFetchDuration(seconds: Double) {
        registry?.let { r ->
            Timer.builder("parallel_fetch_duration_seconds")
                .description("Duration of parallel symbol fetches (e.g. alpha target+benchmark)")
                .tag("app", "meiken")
                .register(r)
                .record((seconds * 1_000).toLong(), TimeUnit.MILLISECONDS)
        }
    }

    fun recordResponseSizeBytes(endpoint: String, sizeBytes: Long) {
        registry?.let { r ->
            io.micrometer.core.instrument.DistributionSummary.builder("response_size_bytes")
                .description("Response body size in bytes")
                .tag("app", "meiken")
                .tag("endpoint", endpoint)
                .register(r)
                .record(sizeBytes.toDouble())
        }
    }

    fun recordParallelOperationsTotal(count: Int) {
        registry?.let { r ->
            Counter.builder("parallel_operations_total")
                .description("Number of parallel operations (e.g. concurrent symbol fetches)")
                .tag("app", "meiken")
                .register(r)
                .increment(count.toDouble())
        }
    }

    // --- Data quality: data_quality_issues_total (type, symbol), data_staleness_seconds, outliers_detected_total ---

    /** Increments data_quality_issues_total. [type] e.g. "unrealistic_price", "duplicate_date", "negative_volume", "large_change", "sparse_data". */
    fun recordDataQualityIssue(type: String, symbol: String) {
        registry?.let { r ->
            Counter.builder("data_quality_issues_total")
                .description("Total data quality issues by type and symbol")
                .tag("app", "meiken")
                .tag("type", type)
                .tag("symbol", symbol)
                .register(r)
                .increment()
        }
    }

    /** Records age in seconds of cached analytics when served (for monitoring staleness). */
    fun recordDataStalenessSeconds(seconds: Double, symbol: String = "") {
        registry?.let { r ->
            io.micrometer.core.instrument.DistributionSummary.builder("data_staleness_seconds")
                .description("Age of cached data in seconds when served")
                .tag("app", "meiken")
                .tag("symbol", symbol)
                .register(r)
                .record(seconds)
        }
    }

    /** Records number of outliers (3-sigma) detected in returns for a symbol (outliers are kept in calculations). */
    fun recordOutliersDetected(count: Int, symbol: String = "") {
        registry?.let { r ->
            Counter.builder("outliers_detected_total")
                .description("Total outliers detected (3-sigma) in returns")
                .tag("app", "meiken")
                .tag("symbol", symbol)
                .register(r)
                .increment(count.toDouble())
        }
    }
}
