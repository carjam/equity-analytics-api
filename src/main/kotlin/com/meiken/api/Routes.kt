package com.meiken.api

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.MarketDataService
import com.meiken.security.ApiKeyManager
import com.meiken.security.apiKeyPrincipal
import com.meiken.security.validateApiKey
import com.meiken.service.AlphaService
import com.meiken.service.AnalyticsService
import com.meiken.service.ReturnsService
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private val healthJson = Json { encodeDefaults = true }

/**
 * Registers all HTTP routes: GET /health (enhanced JSON health), GET /metrics (Prometheus),
 * and under api/v1 the returns, alpha, and analytics route trees.
 */
fun Application.configureRouting(
    returnsService: ReturnsService,
    alphaService: AlphaService,
    analyticsService: AnalyticsService,
    prometheusRegistry: PrometheusMeterRegistry?,
    marketDataService: MarketDataService? = null,
    analyticsCache: SymbolAnalyticsCacheService? = null,
    apiKeysEnabled: Boolean = false,
    apiKeyManager: ApiKeyManager? = null,
    circuitBreaker: io.github.resilience4j.circuitbreaker.CircuitBreaker? = null,
    isShuttingDown: () -> Boolean = { false }
) {
    routing {
        get("/health") {
            val (status, dependencies, system) = buildHealthDetails(marketDataService, analyticsCache, circuitBreaker, isShuttingDown)
            val response = EnhancedHealthResponse(
                status = status,
                timestamp = Instant.now().toString(),
                version = "0.1.0",
                dependencies = dependencies,
                system = system
            )
            val body = healthJson.encodeToString(response)
            val code = if (isShuttingDown()) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK
            call.respondText(body, ContentType.Application.Json, code)
        }
        get("/metrics") {
            val body = prometheusRegistry?.scrape() ?: ""
            call.respondText(body, ContentType.Text.Plain, HttpStatusCode.OK)
        }
        route("api/v1") {
            if (apiKeysEnabled && apiKeyManager != null) {
                intercept(ApplicationCallPipeline.Call) {
                    if (!call.validateApiKey(apiKeyManager)) return@intercept
                    call.apiKeyPrincipal()?.let { principal ->
                        apiKeyManager.recordUsage(principal.keyId, call.request.path())
                    }
                    proceed()
                }
            }
            rateLimit(RateLimitName("api")) {
                returnsRoutes(returnsService)
                alphaRoutes(alphaService)
                analyticsRoutes(analyticsService)
            }
        }
    }
}
