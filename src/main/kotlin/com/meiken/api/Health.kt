package com.meiken.api

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.MarketDataService
import com.meiken.util.getCurrentYearStart
import com.meiken.util.getToday
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlinx.coroutines.runBlocking
import java.lang.management.ManagementFactory

fun buildHealthDetails(
    marketDataService: MarketDataService?,
    analyticsCache: SymbolAnalyticsCacheService?,
    circuitBreaker: CircuitBreaker? = null,
    isShuttingDown: () -> Boolean = { false }
): Triple<String, List<EnhancedDependencyStatus>, SystemStatus> {
    val dependencies = mutableListOf<EnhancedDependencyStatus>()
    var anyDown = false
    var anyDegraded = false

    if (isShuttingDown()) {
        anyDown = true
        dependencies.add(
            EnhancedDependencyStatus(
                name = "application",
                status = "down",
                message = "Application is shutting down"
            )
        )
    }

    if (marketDataService != null && !isShuttingDown()) {
        val cbState = circuitBreaker?.state?.name
        val cbMetrics = circuitBreaker?.metrics
        val failureRate = cbMetrics?.failureRate?.toDouble()
        val slowCallRate = cbMetrics?.slowCallRate?.toDouble()
        val circuitOpen = cbState == "OPEN"
        if (circuitOpen) anyDegraded = true

        val start = System.currentTimeMillis()
        val tri = if (circuitOpen) {
            Triple("down", (System.currentTimeMillis() - start).toLong(), "Circuit breaker OPEN")
        } else try {
            runBlocking {
                val from = getCurrentYearStart()
                val to = getToday()
                marketDataService.getHistoricalPrices("AAPL", from, to)
            }
            Triple("up", (System.currentTimeMillis() - start).toLong(), "OK")
        } catch (e: Exception) {
            anyDown = true
            Triple("down", (System.currentTimeMillis() - start).toLong(), e.message ?: "Check failed")
        }
        dependencies.add(
            EnhancedDependencyStatus(
                name = "alpha_vantage",
                status = tri.first,
                latency_ms = tri.second,
                message = tri.third,
                circuit_breaker = if (circuitBreaker != null) CircuitBreakerHealth(
                    state = circuitBreaker.state.name,
                    failure_rate = failureRate,
                    slow_call_rate = slowCallRate
                ) else null
            )
        )
    }

    if (analyticsCache != null) {
        val cacheSize = analyticsCache.getEstimatedSize()
        val hitRate = analyticsCache.getHitRate()
        dependencies.add(
            EnhancedDependencyStatus(
                name = "cache",
                status = "up",
                message = "Operational",
                size = cacheSize,
                hit_rate = hitRate
            )
        )
    }

    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMb = runtime.maxMemory() / (1024 * 1024)
    val uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000
    val system = SystemStatus(
        memory_used_mb = usedMb,
        memory_max_mb = maxMb,
        uptime_seconds = uptimeSeconds
    )

    val status = when {
        anyDown -> "unhealthy"
        anyDegraded -> "degraded"
        else -> "healthy"
    }
    return Triple(status, dependencies, system)
}
