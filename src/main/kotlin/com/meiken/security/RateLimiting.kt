package com.meiken.security

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.seconds

/**
 * Installs Ktor RateLimit plugin and registers limiters.
 * - "api": for /api/v1 routes (anonymous), 100/min per IP (configurable).
 * - /health and /metrics are not rate-limited (defined outside rateLimit block in routes).
 */
fun Application.installRateLimiting(anonymousPerMinute: Int, authenticatedPerMinute: Int) {
    install(RateLimit) {
        register(RateLimitName("api")) {
            rateLimiter(limit = anonymousPerMinute, refillPeriod = 60.seconds)
        }
        register(RateLimitName("apiAuthenticated")) {
            rateLimiter(limit = authenticatedPerMinute, refillPeriod = 60.seconds)
        }
    }
}
