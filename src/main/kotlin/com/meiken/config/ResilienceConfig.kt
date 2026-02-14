package com.meiken.config

import io.ktor.server.config.ApplicationConfig

/**
 * Resilience configuration read from application.conf (meiken.resilience).
 */
data class ResilienceConfig(
    val circuitBreaker: CircuitBreakerSettings,
    val retry: RetrySettings,
    val timeout: TimeoutSettings,
    val bulkhead: BulkheadSettings
) {
    data class CircuitBreakerSettings(
        val failureRateThreshold: Float,
        val slowCallDurationThresholdMs: Long,
        val minimumNumberOfCalls: Int,
        val waitDurationInOpenStateMs: Long,
        val permittedNumberOfCallsInHalfOpenState: Int
    )
    data class RetrySettings(
        val maxAttempts: Int,
        val waitDurationMs: Long,
        val multiplier: Double,
        val maxWaitDurationMs: Long
    )
    data class TimeoutSettings(
        val requestTimeoutMs: Long,
        val alphaVantageTimeoutMs: Long,
        val connectionTimeoutMs: Long
    )
    data class BulkheadSettings(
        val maxConcurrentCalls: Int,
        val maxWaitDurationMs: Long
    )

    companion object {
        fun from(config: ApplicationConfig): ResilienceConfig {
            val meiken = try {
                config.config("meiken").config("resilience")
            } catch (_: Exception) {
                return ResilienceConfig(
                    circuitBreaker = CircuitBreakerSettings(50f, 5000L, 5, 60000L, 3),
                    retry = RetrySettings(3, 100L, 2.0, 1000L),
                    timeout = TimeoutSettings(60000L, 30000L, 10000L),
                    bulkhead = BulkheadSettings(10, 5000L)
                )
            }
            val circuitBreaker = try {
                val cb = meiken.config("circuitBreaker")
                CircuitBreakerSettings(
                    failureRateThreshold = cb.propertyOrNull("failureRateThreshold")?.getString()?.toFloatOrNull() ?: 50f,
                    slowCallDurationThresholdMs = cb.propertyOrNull("slowCallDurationThreshold")?.getString()?.toLongOrNull() ?: 5000L,
                    minimumNumberOfCalls = cb.propertyOrNull("minimumNumberOfCalls")?.getString()?.toIntOrNull() ?: 5,
                    waitDurationInOpenStateMs = cb.propertyOrNull("waitDurationInOpenState")?.getString()?.toLongOrNull() ?: 60000L,
                    permittedNumberOfCallsInHalfOpenState = cb.propertyOrNull("permittedNumberOfCallsInHalfOpenState")?.getString()?.toIntOrNull() ?: 3
                )
            } catch (_: Exception) {
                CircuitBreakerSettings(50f, 5000L, 5, 60000L, 3)
            }
            val retry = try {
                val r = meiken.config("retry")
                RetrySettings(
                    maxAttempts = r.propertyOrNull("maxAttempts")?.getString()?.toIntOrNull() ?: 3,
                    waitDurationMs = r.propertyOrNull("waitDuration")?.getString()?.toLongOrNull() ?: 100L,
                    multiplier = r.propertyOrNull("multiplier")?.getString()?.toDoubleOrNull() ?: 2.0,
                    maxWaitDurationMs = r.propertyOrNull("maxWaitDuration")?.getString()?.toLongOrNull() ?: 1000L
                )
            } catch (_: Exception) {
                RetrySettings(3, 100L, 2.0, 1000L)
            }
            val timeout = try {
                val t = meiken.config("timeout")
                TimeoutSettings(
                    requestTimeoutMs = t.propertyOrNull("requestTimeout")?.getString()?.toLongOrNull() ?: 60000L,
                    alphaVantageTimeoutMs = t.propertyOrNull("alphaVantageTimeout")?.getString()?.toLongOrNull() ?: 30000L,
                    connectionTimeoutMs = t.propertyOrNull("connectionTimeout")?.getString()?.toLongOrNull() ?: 10000L
                )
            } catch (_: Exception) {
                TimeoutSettings(60000L, 30000L, 10000L)
            }
            val bulkhead = try {
                val b = meiken.config("bulkhead")
                BulkheadSettings(
                    maxConcurrentCalls = b.propertyOrNull("maxConcurrentCalls")?.getString()?.toIntOrNull() ?: 10,
                    maxWaitDurationMs = b.propertyOrNull("maxWaitDuration")?.getString()?.toLongOrNull() ?: 5000L
                )
            } catch (_: Exception) {
                BulkheadSettings(10, 5000L)
            }
            return ResilienceConfig(circuitBreaker, retry, timeout, bulkhead)
        }
    }
}
