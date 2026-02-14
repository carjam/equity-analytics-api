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
