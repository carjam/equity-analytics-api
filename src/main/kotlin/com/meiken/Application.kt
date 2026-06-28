package com.meiken

import com.meiken.api.configureRouting
import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.AlphaVantageService
import com.meiken.data.MockMarketDataService
import com.meiken.data.MarketDataService
import com.meiken.error.BadRequestException
import com.meiken.error.DataRetrievalException
import com.meiken.error.ErrorDetail
import com.meiken.error.ErrorResponse
import com.meiken.error.ExternalServiceException
import com.meiken.error.CircuitBreakerOpenException
import com.meiken.error.InvalidDateRangeException
import com.meiken.error.RateLimitExceededException
import com.meiken.error.RetryExhaustedException
import com.meiken.error.SymbolNotFoundException
import com.meiken.error.UnauthorizedException
import com.meiken.security.ApiKeyManager
import com.meiken.security.SecurityConfig
import com.meiken.security.TlsConfig
import com.meiken.security.createSecurityHeadersPlugin
import com.meiken.security.installRateLimiting
import com.meiken.config.ApiConfig
import com.meiken.config.CacheConfig
import com.meiken.config.CalculationsConfig
import com.meiken.config.DataQualityConfig
import com.meiken.config.DateRangesConfig
import com.meiken.config.DefaultsConfig
import com.meiken.config.HttpClientConfig
import com.meiken.config.PerformanceConfig
import com.meiken.config.ResilienceConfig
import com.meiken.resilience.CircuitBreakerConfig
import com.meiken.resilience.RetryConfig
import com.meiken.data.ResilientMarketDataService
import com.meiken.lifecycle.ShutdownState
import com.meiken.observability.Metrics
import com.meiken.observability.ObservabilityPlugin
import com.meiken.service.AlphaServiceImpl
import com.meiken.service.AnalyticsServiceImpl
import com.meiken.service.ReturnsServiceImpl
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import java.util.concurrent.TimeoutException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/**
 * Registers Ktor StatusPages to map exceptions to HTTP error responses (JSON).
 * InvalidDateRangeException/BadRequestException -> 400, SymbolNotFoundException -> 404,
 * DataRetrievalException -> 500, ExternalServiceException -> 502, other Throwable -> 500.
 */
fun Application.installStatusPages(maxDays: Int = 365) {
    install(StatusPages) {
        exception<InvalidDateRangeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail(
                    "INVALID_DATE_RANGE",
                    cause.message ?: "Invalid date range. Use from_date ≤ to_date, no future dates, and span at most $maxDays days."
                ))
            )
        }
        exception<SymbolNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(ErrorDetail("SYMBOL_NOT_FOUND", cause.message ?: "Symbol not found. Check the ticker and try again."))
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("BAD_REQUEST", cause.message ?: "Bad request. Check parameters and format (e.g. YYYY-MM-DD for dates)."))
            )
        }
        exception<DataRetrievalException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("DATA_RETRIEVAL_ERROR", cause.message ?: "Data retrieval failed. Please try again later."))
            )
        }
        exception<ExternalServiceException> { call, cause ->
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(ErrorDetail("EXTERNAL_SERVICE_ERROR", cause.message ?: "External service error. Please try again later."))
            )
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(ErrorDetail("UNAUTHORIZED", cause.message ?: "Missing or invalid API key."))
            )
        }
        exception<RateLimitExceededException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.headers.append(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(
                HttpStatusCode.TooManyRequests,
                ErrorResponse(ErrorDetail("RATE_LIMIT_EXCEEDED", cause.message ?: "Rate limit exceeded. Retry later."))
            )
        }
        exception<CircuitBreakerOpenException> { call, cause ->
            cause.retryAfterSeconds?.let { call.response.headers.append(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ErrorResponse(ErrorDetail(
                    "SERVICE_UNAVAILABLE",
                    cause.message ?: "Alpha Vantage service is temporarily unavailable due to repeated failures. Please try again later.",
                    details = mapOf(
                        "retry_after_seconds" to (cause.retryAfterSeconds?.toString() ?: "60"),
                        "circuit_breaker_state" to cause.circuitState
                    )
                ))
            )
        }
        exception<TimeoutException> { call, cause ->
            Metrics.recordRequestTimeout()
            call.respond(
                HttpStatusCode.GatewayTimeout,
                ErrorResponse(ErrorDetail("GATEWAY_TIMEOUT", cause.message ?: "Request timed out. Please try again later."))
            )
        }
        exception<RetryExhaustedException> { call, cause ->
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(ErrorDetail("RETRY_EXHAUSTED", cause.message ?: "Request failed after retries. Please try again later."))
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("INTERNAL_ERROR", cause.message ?: "An unexpected error occurred. Please try again later."))
            )
        }
    }
}

/**
 * Main application module: installs ContentNegotiation (JSON), StatusPages, CallLogging, CORS,
 * then creates MarketDataService (Alpha Vantage if ALPHA_VANTAGE_API_KEY set, else Mock) and wires routing.
 * A single [SymbolAnalyticsCacheService] is shared by Returns, Alpha, and Analytics so that each
 * symbol/date-range is fetched and computed once; all endpoints reuse cached analytics (one API call
 * per symbol/range, no redundant calculations, better rate-limit behavior).
 */
fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    val performanceConfig = PerformanceConfig.from(environment.config)
    if (performanceConfig.compression.enabled) {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 0.9 }
        }
    }

    val dateRangesConfig = DateRangesConfig.from(environment.config)
    installStatusPages(dateRangesConfig.maxDays)
    install(CallLogging)

    val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    Metrics.init(prometheusRegistry)
    install(MicrometerMetrics) {
        registry = prometheusRegistry
    }
    install(ObservabilityPlugin)

    val securityConfig = SecurityConfig.from(environment.config)
    val tlsConfig = TlsConfig.from(environment.config)
    val startupLog = LoggerFactory.getLogger("com.meiken.Application")
    if (!tlsConfig.isConfigured) {
        startupLog.warn("SSL/TLS not configured (no KEY_STORE_PATH); running in HTTP-only development mode. For production, use a reverse proxy for HTTPS.")
    }
    if (!securityConfig.apiKeysEnabled) {
        startupLog.warn("API key authentication is DISABLED. All /api/v1/** endpoints are publicly accessible. Set API_KEYS_ENABLED=true and VALID_API_KEYS before exposing this service externally.")
    }
    install(createSecurityHeadersPlugin(addHsts = securityConfig.requireHttps))
    installRateLimiting(
        anonymousPerMinute = securityConfig.rateLimitAnonymousPerMinute,
        authenticatedPerMinute = securityConfig.rateLimitAuthenticatedPerMinute
    )
    install(CORS) {
        if (securityConfig.allowedOrigins.isNotEmpty()) {
            securityConfig.allowedOrigins.forEach { allowHost(it) }
        } else {
            anyHost()
        }
        allowMethod(io.ktor.http.HttpMethod.Get)
        allowHeader("X-API-Key")
        allowHeader("X-Correlation-ID")
    }

    val apiKeyManager = ApiKeyManager(securityConfig.validApiKeys)

    val resilienceConfig = ResilienceConfig.from(environment.config)
    val circuitBreaker = CircuitBreakerConfig.createCircuitBreaker(resilienceConfig.circuitBreaker)
    val retry = RetryConfig.createRetry(resilienceConfig.retry)

    val cacheConfig = CacheConfig.from(environment.config)
    val dataQualityConfig = DataQualityConfig.from(environment.config)
    val calculationsConfig = CalculationsConfig.from(environment.config)
    val defaultsConfig = DefaultsConfig.from(environment.config)
    val apiConfig = ApiConfig.from(environment.config)
    val httpClientConfig = HttpClientConfig.from(environment.config)
    val maxDays = dateRangesConfig.maxDays
    val alphaVantageConfig = try {
        environment.config.config("meiken").config("alphaVantage")
    } catch (_: Exception) {
        null
    }
    val alphaVantageBaseUrl = alphaVantageConfig?.propertyOrNull("baseUrl")?.getString() ?: "https://www.alphavantage.co/query"
    val rawResponseLogLimit = alphaVantageConfig?.propertyOrNull("rawResponseLogLimit")?.getString()?.toIntOrNull() ?: 500

    Runtime.getRuntime().addShutdownHook(Thread {
        ShutdownState.signalShutdown()
    })

    val environmentName = environment.config.propertyOrNull("meiken.environment")?.getString() ?: "development"
    val marketDataService = createMarketDataService(
        environmentName,
        resilienceConfig,
        circuitBreaker,
        retry,
        dataQualityConfig,
        alphaVantageBaseUrl,
        rawResponseLogLimit,
        cacheConfig.sparseRatio,
        httpClientConfig
    )
    val analyticsCache = SymbolAnalyticsCacheService(cacheConfig, calculationsConfig, dataQualityConfig)
    val returnsService = ReturnsServiceImpl(analyticsCache, marketDataService, maxDays)
    val alphaService = AlphaServiceImpl(analyticsCache, marketDataService, maxDays, calculationsConfig.tradingDaysPerYear)
    val analyticsService = AnalyticsServiceImpl(analyticsCache, marketDataService, maxDays)
    configureRouting(
        returnsService = returnsService,
        alphaService = alphaService,
        analyticsService = analyticsService,
        prometheusRegistry = prometheusRegistry,
        marketDataService = marketDataService,
        analyticsCache = analyticsCache,
        apiKeysEnabled = securityConfig.apiKeysEnabled,
        apiKeyManager = apiKeyManager,
        circuitBreaker = circuitBreaker,
        isShuttingDown = { ShutdownState.isShuttingDown() },
        defaultsConfig = defaultsConfig,
        securityConfig = securityConfig,
        apiConfig = apiConfig
    )
}

/**
 * Creates [MarketDataService]: Alpha Vantage (with circuit breaker, retry, timeouts) when ALPHA_VANTAGE_API_KEY is set, else Mock.
 */
private fun createMarketDataService(
    environmentName: String,
    resilienceConfig: ResilienceConfig,
    circuitBreaker: io.github.resilience4j.circuitbreaker.CircuitBreaker,
    retry: io.github.resilience4j.retry.Retry,
    dataQualityConfig: DataQualityConfig,
    alphaVantageBaseUrl: String,
    rawResponseLogLimit: Int,
    sparseRatio: Double,
    httpClientConfig: HttpClientConfig
): MarketDataService {
    val apiKey = System.getenv("ALPHA_VANTAGE_API_KEY")
    val isProduction = environmentName.equals("production", ignoreCase = true)
    return if (!apiKey.isNullOrBlank()) {
        val timeout = resilienceConfig.timeout
        val client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = timeout.alphaVantageTimeoutMs
                connectTimeoutMillis = timeout.connectionTimeoutMs
                socketTimeoutMillis = timeout.alphaVantageTimeoutMs
            }
            engine {
                endpoint {
                    connectTimeout = timeout.connectionTimeoutMs
                    socketTimeout = timeout.alphaVantageTimeoutMs
                    keepAliveTime = httpClientConfig.keepAliveTimeMs
                    maxConnectionsPerRoute = httpClientConfig.maxConnectionsPerRoute
                }
            }
        }
        val delegate = AlphaVantageService(
            client = client,
            apiKey = apiKey,
            baseUrl = alphaVantageBaseUrl,
            outputSize = if (isProduction) "full" else "compact",
            useLimiterMessages = !isProduction,
            dataQualityConfig = dataQualityConfig,
            rawResponseLogLimit = rawResponseLogLimit,
            sparseRatio = sparseRatio
        )
        val waitSeconds = (resilienceConfig.circuitBreaker.waitDurationInOpenStateMs / 1000).toInt()
        ResilientMarketDataService(delegate, circuitBreaker, retry, waitSeconds)
    } else {
        LoggerFactory.getLogger("com.meiken.Application")
            .warn("ALPHA_VANTAGE_API_KEY not set; using MockMarketDataService for development")
        MockMarketDataService()
    }
}
