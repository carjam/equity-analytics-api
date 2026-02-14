package com.meiken.data

import com.meiken.error.CircuitBreakerOpenException
import com.meiken.model.DailyPrice
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import kotlinx.datetime.LocalDate

/**
 * Wraps a [MarketDataService] (e.g. [AlphaVantageService]) with circuit breaker and retry.
 * When the circuit is OPEN, throws [CircuitBreakerOpenException] (503).
 * When retries are exhausted, throws [RetryExhaustedException] (502).
 */
class ResilientMarketDataService(
    private val delegate: MarketDataService,
    private val circuitBreaker: CircuitBreaker,
    private val retry: Retry,
    private val waitDurationInOpenStateSeconds: Int
) : MarketDataService {

    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate): List<DailyPrice> {
        return try {
            circuitBreaker.executeSuspendFunction {
                retry.executeSuspendFunction {
                    delegate.getHistoricalPrices(symbol, fromDate, toDate)
                }
            }
        } catch (e: CallNotPermittedException) {
            throw CircuitBreakerOpenException(
                message = "Alpha Vantage service is temporarily unavailable due to repeated failures. Please try again later.",
                retryAfterSeconds = waitDurationInOpenStateSeconds,
                circuitState = circuitBreaker.state.name
            )
        } catch (e: Exception) {
            if (e is CircuitBreakerOpenException) throw e
            throw e
        }
    }
}
