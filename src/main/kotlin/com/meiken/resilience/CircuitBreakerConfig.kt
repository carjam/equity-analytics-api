package com.meiken.resilience

import com.meiken.config.ResilienceConfig
import com.meiken.observability.Metrics
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import java.time.Duration

private const val ALPHA_VANTAGE_CIRCUIT = "alphaVantage"

/**
 * Creates and configures the CircuitBreaker for Alpha Vantage API calls.
 * Emits metrics (circuit_breaker_state, circuit_breaker_calls) and logs state transitions.
 */
object CircuitBreakerConfig {

    private val log = LoggerFactory.getLogger(CircuitBreakerConfig::class.java)

    fun createCircuitBreaker(settings: ResilienceConfig.CircuitBreakerSettings): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .failureRateThreshold(settings.failureRateThreshold)
            .slowCallDurationThreshold(Duration.ofMillis(settings.slowCallDurationThresholdMs))
            .minimumNumberOfCalls(settings.minimumNumberOfCalls)
            .waitDurationInOpenState(Duration.ofMillis(settings.waitDurationInOpenStateMs))
            .permittedNumberOfCallsInHalfOpenState(settings.permittedNumberOfCallsInHalfOpenState)
            .build()

        val registry = CircuitBreakerRegistry.of(config)
        val circuitBreaker = registry.circuitBreaker(ALPHA_VANTAGE_CIRCUIT)

        circuitBreaker.eventPublisher
            .onStateTransition { event ->
                log.warn("Circuit breaker {}: {} -> {}", ALPHA_VANTAGE_CIRCUIT, event.stateTransition.fromState, event.stateTransition.toState)
                Metrics.recordCircuitBreakerState(circuitBreaker.state.name)
            }

        circuitBreaker.eventPublisher
            .onSuccess { Metrics.recordCircuitBreakerCall("success", "success") }
            .onError { Metrics.recordCircuitBreakerCall("error", "error") }
            .onCallNotPermitted { Metrics.recordCircuitBreakerCall("call_not_permitted", "call_not_permitted") }

        Metrics.registerCircuitBreakerGauge(ALPHA_VANTAGE_CIRCUIT) { circuitBreaker.state.ordinal.toDouble() }
        return circuitBreaker
    }

    fun circuitName(): String = ALPHA_VANTAGE_CIRCUIT
}
