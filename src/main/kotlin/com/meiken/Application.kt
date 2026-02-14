package com.meiken

import com.meiken.api.configureRouting
import com.meiken.data.AlphaVantageService
import com.meiken.data.MockMarketDataService
import com.meiken.data.MarketDataService
import com.meiken.error.BadRequestException
import com.meiken.error.DataRetrievalException
import com.meiken.error.ErrorDetail
import com.meiken.error.ErrorResponse
import com.meiken.error.ExternalServiceException
import com.meiken.error.InvalidDateRangeException
import com.meiken.error.SymbolNotFoundException
import com.meiken.service.AlphaServiceImpl
import com.meiken.service.AnalyticsServiceImpl
import com.meiken.service.ReturnsServiceImpl
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.client.engine.cio.CIO
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

/**
 * Registers Ktor StatusPages to map exceptions to HTTP error responses (JSON).
 * InvalidDateRangeException/BadRequestException -> 400, SymbolNotFoundException -> 404,
 * DataRetrievalException -> 500, ExternalServiceException -> 502, other Throwable -> 500.
 */
fun Application.installStatusPages() {
    install(StatusPages) {
        exception<InvalidDateRangeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail(
                    "INVALID_DATE_RANGE",
                    cause.message ?: "Invalid date range. Use from_date ≤ to_date, no future dates, and span at most 365 days."
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
 */
fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    installStatusPages()
    install(CallLogging)
    install(CORS) {
        anyHost()
    }

    val marketDataService = createMarketDataService()
    val returnsService = ReturnsServiceImpl(marketDataService)
    val alphaService = AlphaServiceImpl(marketDataService)
    val analyticsService = AnalyticsServiceImpl(marketDataService)
    configureRouting(returnsService, alphaService, analyticsService)
}

/**
 * Uses Alpha Vantage when ALPHA_VANTAGE_API_KEY is set; otherwise logs a warning and uses MockMarketDataService for development.
 */
private fun createMarketDataService(): MarketDataService {
    val apiKey = System.getenv("ALPHA_VANTAGE_API_KEY")
    return if (!apiKey.isNullOrBlank()) {
        val client = HttpClient(CIO) { }
        AlphaVantageService(client, apiKey)
    } else {
        LoggerFactory.getLogger("com.meiken.Application")
            .warn("ALPHA_VANTAGE_API_KEY not set; using MockMarketDataService for development")
        MockMarketDataService()
    }
}
