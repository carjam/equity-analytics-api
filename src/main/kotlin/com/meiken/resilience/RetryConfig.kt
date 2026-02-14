package com.meiken.resilience

import com.meiken.config.ResilienceConfig
import com.meiken.error.DataRetrievalException
import com.meiken.error.SymbolNotFoundException
import io.github.resilience4j.retry.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Creates Retry with exponential backoff for Alpha Vantage calls.
 * Retries on: IOException, SocketTimeoutException, rate limit (429) / transient DataRetrievalException.
 * Does not retry on: SymbolNotFoundException, 4xx client errors (except 429), validation errors.
 */
object RetryConfig {

    fun createRetry(settings: ResilienceConfig.RetrySettings): Retry {
        val intervalFn = IntervalFunction.ofExponentialBackoff(
            settings.waitDurationMs,
            settings.multiplier
        )
        val config = RetryConfig.custom<Any>()
            .maxAttempts(settings.maxAttempts)
            .intervalFunction(intervalFn)
            .retryOnException { e ->
                when (e) {
                    is SocketTimeoutException,
                    is IOException -> true
                    is DataRetrievalException -> true
                    is SymbolNotFoundException -> false
                    else -> e.cause is IOException || e.cause is SocketTimeoutException
                }
            }
            .build()

        return Retry.of("alphaVantage", config)
    }
}
