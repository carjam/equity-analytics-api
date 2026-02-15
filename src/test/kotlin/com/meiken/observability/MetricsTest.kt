package com.meiken.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MetricsTest {

    private val registry = SimpleMeterRegistry()

    @BeforeEach
    fun initMetrics() {
        Metrics.init(registry)
    }

    @Test
    fun `getRegistry returns registry after init`() {
        assertNotNull(Metrics.getRegistry())
        assertEquals(registry, Metrics.getRegistry())
    }

    @Test
    fun `recordApiRequest records counter and timer`() {
        Metrics.recordApiRequest("/api/v1/returns", "200", "GET", 0.05)
        val counter = registry.find("api_requests_total").tag("endpoint", "/api/v1/returns").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `incrementInFlight and decrementInFlight update gauge`() {
        Metrics.incrementInFlight()
        Metrics.incrementInFlight()
        Metrics.decrementInFlight()
        val gauge = registry.find("in_flight_requests").gauge()
        assertNotNull(gauge)
        assertEquals(1.0, gauge!!.value())
    }

    @Test
    fun `recordCacheHit and recordCacheMiss record counters`() {
        Metrics.recordCacheHit()
        Metrics.recordCacheHit()
        Metrics.recordCacheMiss()
        val hits = registry.find("cache_hits_total").counter()
        val misses = registry.find("cache_misses_total").counter()
        assertNotNull(hits)
        assertNotNull(misses)
        assertEquals(2.0, hits!!.count())
        assertEquals(1.0, misses!!.count())
    }

    @Test
    fun `setCacheSize and setCacheHitRate update gauges`() {
        Metrics.setCacheSize(42)
        Metrics.setCacheHitRate(0.85)
        val sizeGauge = registry.find("cache_size").gauge()
        val rateGauge = registry.find("cache_hit_rate").gauge()
        assertNotNull(sizeGauge)
        assertNotNull(rateGauge)
        assertEquals(42.0, sizeGauge!!.value())
        assertEquals(0.85, rateGauge!!.value())
    }

    @Test
    fun `recordAlphaVantageCall and recordAlphaVantageDuration record metrics`() {
        Metrics.recordAlphaVantageCall("AAPL", "success")
        Metrics.recordAlphaVantageDuration(1.5)
        val counter = registry.find("alpha_vantage_calls_total").tag("symbol", "AAPL").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter!!.count())
    }

    @Test
    fun `recordApiKeyUsage and recordApiKeyAuthFailure record metrics`() {
        Metrics.recordApiKeyUsage("key1", "/api/v1/returns")
        Metrics.recordApiKeyAuthFailure()
        val usage = registry.find("api_key_usage_total").counter()
        val failures = registry.find("api_key_authentication_failures_total").counter()
        assertNotNull(usage)
        assertNotNull(failures)
        assertEquals(1.0, usage!!.count())
        assertEquals(1.0, failures!!.count())
    }

    @Test
    fun `recordCircuitBreakerState and recordCircuitBreakerCall record metrics`() {
        Metrics.recordCircuitBreakerState("OPEN")
        Metrics.recordCircuitBreakerCall("success", "success")
        val callCounter = registry.find("circuit_breaker_calls_total").counter()
        assertNotNull(callCounter)
    }

    @Test
    fun `registerCircuitBreakerGauge registers gauge`() {
        Metrics.registerCircuitBreakerGauge("test") { 1.0 }
        val gauge = registry.find("circuit_breaker_state_gauge").tag("name", "test").gauge()
        assertNotNull(gauge)
        assertEquals(1.0, gauge!!.value())
    }

    @Test
    fun `recordRetryAttempt and recordRequestTimeout record metrics`() {
        Metrics.recordRetryAttempt("success")
        Metrics.recordRequestTimeout()
        val retry = registry.find("retry_attempts_total").counter()
        val timeout = registry.find("request_timeout_total").counter()
        assertNotNull(retry)
        assertNotNull(timeout)
    }

    @Test
    fun `recordGracefulShutdownDuration records timer`() {
        Metrics.recordGracefulShutdownDuration(5.0)
        val timer = registry.find("graceful_shutdown_duration_seconds").timer()
        assertNotNull(timer)
        assertEquals(1, timer!!.count())
    }

    @Test
    fun `recordParallelFetchDuration and recordParallelOperationsTotal record metrics`() {
        Metrics.recordParallelFetchDuration(0.1)
        Metrics.recordParallelOperationsTotal(2)
        val parallelOps = registry.find("parallel_operations_total").counter()
        assertNotNull(parallelOps)
        assertEquals(2.0, parallelOps!!.count())
    }

    @Test
    fun `recordResponseSizeBytes records distribution summary`() {
        Metrics.recordResponseSizeBytes("/api/v1/returns", 1024L)
        val summary = registry.find("response_size_bytes").tag("endpoint", "/api/v1/returns").summary()
        assertNotNull(summary)
        assertEquals(1, summary!!.count())
    }

    @Test
    fun `recordDataQualityIssue and recordDataStalenessSeconds and recordOutliersDetected record metrics`() {
        Metrics.recordDataQualityIssue("unrealistic_price", "AAPL")
        Metrics.recordDataStalenessSeconds(3600.0, "AAPL")
        Metrics.recordOutliersDetected(2, "AAPL")
        val issues = registry.find("data_quality_issues_total").tag("type", "unrealistic_price").counter()
        val staleness = registry.find("data_staleness_seconds").summary()
        val outliers = registry.find("outliers_detected_total").counter()
        assertNotNull(issues)
        assertNotNull(staleness)
        assertNotNull(outliers)
    }

    @Test
    fun `recordCircuitBreakerState with all state values`() {
        Metrics.recordCircuitBreakerState("CLOSED")
        Metrics.recordCircuitBreakerState("OPEN")
        Metrics.recordCircuitBreakerState("HALF_OPEN")
        Metrics.recordCircuitBreakerState("unknown")
    }

    @Test
    fun `recordDataStalenessSeconds and recordOutliersDetected with empty symbol`() {
        Metrics.recordDataStalenessSeconds(100.0, "")
        Metrics.recordOutliersDetected(0, "")
    }

}
