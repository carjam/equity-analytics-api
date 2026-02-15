package com.meiken.data

import com.meiken.error.CircuitBreakerOpenException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResilientMarketDataServiceTest {

    @Test
    fun `getHistoricalPrices returns delegate result when circuit closed`() = runBlocking {
        val prices = listOf(
            com.meiken.model.DailyPrice(LocalDate(2024, 1, 1), 100.0),
            com.meiken.model.DailyPrice(LocalDate(2024, 1, 2), 101.0)
        )
        val delegate = object : MarketDataService {
            override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate) = prices
        }
        val circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom().build())
        val retry = Retry.of("test", RetryConfig.custom<Any>().maxAttempts(1).build())
        val service = ResilientMarketDataService(delegate, circuitBreaker, retry, 60)
        val result = service.getHistoricalPrices("AAPL", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
        assertEquals(2, result.size)
        assertEquals(100.0, result[0].close)
    }

    @Test
    fun `getHistoricalPrices throws CircuitBreakerOpenException when circuit open`() = runBlocking {
        val delegate = object : MarketDataService {
            override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate) =
                throw RuntimeException("fail")
        }
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .minimumNumberOfCalls(1)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(60))
            .slidingWindowSize(2)
            .build()
        val circuitBreaker = CircuitBreaker.of("test-open", config)
        val retry = Retry.of("test-open", RetryConfig.custom<Any>().maxAttempts(1).build())
        val service = ResilientMarketDataService(delegate, circuitBreaker, retry, 60)
        repeat(2) {
            try {
                service.getHistoricalPrices("X", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
            } catch (_: Exception) { }
        }
        val ex = assertFailsWith<CircuitBreakerOpenException> {
            service.getHistoricalPrices("X", LocalDate(2024, 1, 1), LocalDate(2024, 1, 2))
        }
        assertEquals(60, ex.retryAfterSeconds)
        assertEquals("OPEN", ex.circuitState)
    }
}
