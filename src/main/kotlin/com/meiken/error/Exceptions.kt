package com.meiken.error

/**
 * Thrown when the requested date range is invalid (e.g. from > to, or too large).
 */
class InvalidDateRangeException(message: String) : RuntimeException(message)

/**
 * Thrown when a ticker symbol is not found (e.g. from data provider).
 */
class SymbolNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when the request has invalid parameters (e.g. missing required query param).
 */
class BadRequestException(message: String) : RuntimeException(message)

/**
 * Thrown when an external service (e.g. Alpha Vantage) fails or returns an error.
 */
class ExternalServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when market data retrieval fails (network, rate limit, parse error, etc.).
 */
class DataRetrievalException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown when API key is missing or invalid (401 Unauthorized).
 */
class UnauthorizedException(message: String = "Missing or invalid API key") : RuntimeException(message)

/**
 * Thrown when rate limit is exceeded (429 Too Many Requests).
 */
class RateLimitExceededException(message: String = "Rate limit exceeded", val retryAfterSeconds: Int? = null) : RuntimeException(message)

/**
 * Thrown when the circuit breaker is OPEN (Alpha Vantage temporarily unavailable).
 */
class CircuitBreakerOpenException(
    message: String = "Alpha Vantage service is temporarily unavailable due to repeated failures",
    val retryAfterSeconds: Int? = null,
    val circuitState: String = "OPEN"
) : RuntimeException(message)

/**
 * Thrown when all retry attempts for an external call have been exhausted.
 */
class RetryExhaustedException(message: String = "Request failed after retries", cause: Throwable? = null) : RuntimeException(message, cause)
