package com.meiken.api

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.MarketDataService
import com.meiken.service.AlphaService
import com.meiken.service.AnalyticsService
import com.meiken.service.ReturnsService
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
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
    analyticsCache: SymbolAnalyticsCacheService? = null
) {
    routing {
        get("/health") {
            val (status, dependencies, system) = buildHealthDetails(marketDataService, analyticsCache)
            val response = EnhancedHealthResponse(
                status = status,
                timestamp = Instant.now().toString(),
                version = "0.1.0",
                dependencies = dependencies,
                system = system
            )
            val body = healthJson.encodeToString(response)
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.OK)
        }
        get("/metrics") {
            val body = prometheusRegistry?.scrape() ?: ""
            call.respondText(body, ContentType.Text.Plain, HttpStatusCode.OK)
        }
        route("api/v1") {
            returnsRoutes(returnsService)
            alphaRoutes(alphaService)
            analyticsRoutes(analyticsService)
        }
    }
}
