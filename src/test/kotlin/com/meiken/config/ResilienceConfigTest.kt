package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ResilienceConfigTest {

    @Test
    fun `from returns defaults when meiken resilience config missing`() {
        val config = MapApplicationConfig()
        val result = ResilienceConfig.from(config)
        assertEquals(50f, result.circuitBreaker.failureRateThreshold)
        assertEquals(5000L, result.circuitBreaker.slowCallDurationThresholdMs)
        assertEquals(5, result.circuitBreaker.minimumNumberOfCalls)
        assertEquals(60000L, result.circuitBreaker.waitDurationInOpenStateMs)
        assertEquals(3, result.circuitBreaker.permittedNumberOfCallsInHalfOpenState)
        assertEquals(3, result.retry.maxAttempts)
        assertEquals(100L, result.retry.waitDurationMs)
        assertEquals(2.0, result.retry.multiplier)
        assertEquals(1000L, result.retry.maxWaitDurationMs)
        assertEquals(60000L, result.timeout.requestTimeoutMs)
        assertEquals(30000L, result.timeout.alphaVantageTimeoutMs)
        assertEquals(10000L, result.timeout.connectionTimeoutMs)
        assertEquals(10, result.bulkhead.maxConcurrentCalls)
        assertEquals(5000L, result.bulkhead.maxWaitDurationMs)
    }

    @Test
    fun `from reads full resilience when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.resilience.circuitBreaker.failureRateThreshold", "60")
        config.put("meiken.resilience.circuitBreaker.slowCallDurationThreshold", "3000")
        config.put("meiken.resilience.circuitBreaker.minimumNumberOfCalls", "10")
        config.put("meiken.resilience.circuitBreaker.waitDurationInOpenState", "45000")
        config.put("meiken.resilience.circuitBreaker.permittedNumberOfCallsInHalfOpenState", "5")
        config.put("meiken.resilience.retry.maxAttempts", "5")
        config.put("meiken.resilience.retry.waitDuration", "200")
        config.put("meiken.resilience.retry.multiplier", "3.0")
        config.put("meiken.resilience.retry.maxWaitDuration", "2000")
        config.put("meiken.resilience.timeout.requestTimeout", "30000")
        config.put("meiken.resilience.timeout.alphaVantageTimeout", "15000")
        config.put("meiken.resilience.timeout.connectionTimeout", "5000")
        config.put("meiken.resilience.bulkhead.maxConcurrentCalls", "20")
        config.put("meiken.resilience.bulkhead.maxWaitDuration", "3000")
        val result = ResilienceConfig.from(config)
        assertEquals(60f, result.circuitBreaker.failureRateThreshold)
        assertEquals(3000L, result.circuitBreaker.slowCallDurationThresholdMs)
        assertEquals(10, result.circuitBreaker.minimumNumberOfCalls)
        assertEquals(45000L, result.circuitBreaker.waitDurationInOpenStateMs)
        assertEquals(5, result.circuitBreaker.permittedNumberOfCallsInHalfOpenState)
        assertEquals(5, result.retry.maxAttempts)
        assertEquals(200L, result.retry.waitDurationMs)
        assertEquals(3.0, result.retry.multiplier)
        assertEquals(2000L, result.retry.maxWaitDurationMs)
        assertEquals(30000L, result.timeout.requestTimeoutMs)
        assertEquals(15000L, result.timeout.alphaVantageTimeoutMs)
        assertEquals(5000L, result.timeout.connectionTimeoutMs)
        assertEquals(20, result.bulkhead.maxConcurrentCalls)
        assertEquals(3000L, result.bulkhead.maxWaitDurationMs)
    }

    @Test
    fun `from uses default circuitBreaker when subsection throws`() {
        val config = MapApplicationConfig()
        config.put("meiken.resilience.retry.maxAttempts", "4")
        val result = ResilienceConfig.from(config)
        assertEquals(50f, result.circuitBreaker.failureRateThreshold)
        assertEquals(4, result.retry.maxAttempts)
    }
}
