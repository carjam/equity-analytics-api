package com.meiken.resilience

import com.meiken.config.ResilienceConfig
import com.meiken.observability.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CircuitBreakerConfigTest {

    private val registry = SimpleMeterRegistry()

    @BeforeEach
    fun initMetrics() {
        Metrics.init(registry)
    }

    @Test
    fun `createCircuitBreaker returns configured circuit breaker`() {
        val settings = ResilienceConfig.CircuitBreakerSettings(
            failureRateThreshold = 50f,
            slowCallDurationThresholdMs = 5000L,
            minimumNumberOfCalls = 5,
            waitDurationInOpenStateMs = 60000L,
            permittedNumberOfCallsInHalfOpenState = 3
        )
        val breaker = CircuitBreakerConfig.createCircuitBreaker(settings)
        assertNotNull(breaker)
        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED, breaker.state)
    }

    @Test
    fun `circuitName returns alphaVantage`() {
        assertEquals("alphaVantage", CircuitBreakerConfig.circuitName())
    }
}
