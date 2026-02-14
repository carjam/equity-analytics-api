package com.meiken.api

import com.meiken.cache.SymbolAnalyticsCacheService
import com.meiken.data.MarketDataService
import com.meiken.module
import com.meiken.data.MockMarketDataService
import com.meiken.error.SymbolNotFoundException
import com.meiken.installStatusPages
import com.meiken.service.AlphaServiceImpl
import com.meiken.service.AnalyticsServiceImpl
import com.meiken.service.ReturnsServiceImpl
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MarketDataService that throws [SymbolNotFoundException] for given symbols; delegates to [MockMarketDataService] otherwise.
 */
private class ThrowingMarketDataService(
    private val throwForSymbols: Set<String>,
    private val delegate: MarketDataService = MockMarketDataService()
) : MarketDataService {
    override suspend fun getHistoricalPrices(symbol: String, fromDate: LocalDate, toDate: LocalDate) =
        if (symbol.uppercase() in throwForSymbols) {
            throw SymbolNotFoundException("Symbol $symbol not found")
        } else {
            delegate.getHistoricalPrices(symbol, fromDate, toDate)
        }
}

class ReturnsRoutesTest {

    @Test
    fun `successful request returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("AAPL"))
        assertTrue(body.contains("dailyReturns"))
        assertTrue(body.contains("metadata"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `missing or invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        // Empty path segment hits 404 in Ktor; test invalid symbol (too long) for 400
        val response = client.get("/api/v1/tickers/INVALID6/returns")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("BAD_REQUEST") || body.contains("Invalid symbol"))
    }

    @Test
    fun `invalid symbol format returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONGSYMBOL/returns")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `invalid date format returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns?from_date=not-a-date")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("BAD_REQUEST") || body.contains("Invalid date"))
    }

    @Test
    fun `date range over 365 days returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns?from_date=2020-01-01&to_date=2021-06-01")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("INVALID_DATE_RANGE") || body.contains("exceed"))
    }

    @Test
    fun `symbol not found returns 404`() = testApplication {
        environment { config = MapApplicationConfig() }
        val marketData = ThrowingMarketDataService(throwForSymbols = setOf("NONE"))
        val analyticsCache = SymbolAnalyticsCacheService()
        val returnsService = ReturnsServiceImpl(analyticsCache, marketData)
        val alphaService = AlphaServiceImpl(analyticsCache, marketData)
        val analyticsService = AnalyticsServiceImpl(analyticsCache, marketData)
        application {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            installStatusPages()
            routing {
                route("api/v1") {
                    returnsRoutes(returnsService)
                    alphaRoutes(alphaService)
                    analyticsRoutes(analyticsService)
                }
            }
        }
        val response = client.get("/api/v1/tickers/NONE/returns")
        assertEquals(HttpStatusCode.NotFound, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("SYMBOL_NOT_FOUND"))
    }
}
