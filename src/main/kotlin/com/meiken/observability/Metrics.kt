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

    fun getRegistry(): MeterRegistry? = registry
}
