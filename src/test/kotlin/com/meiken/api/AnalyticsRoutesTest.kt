package com.meiken.api

import com.meiken.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnalyticsRoutesTest {

    @Test
    fun `volatility returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/volatility")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("volatility"))
        assertTrue(body.contains("daily"))
        assertTrue(body.contains("annualized"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `beta returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/beta?target=AAPL&benchmark=SPY")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("target"))
        assertTrue(body.contains("benchmark"))
        assertTrue(body.contains("beta"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["target"]?.jsonPrimitive?.content)
        assertEquals("SPY", json["benchmark"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sharpe returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/sharpe")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("sharpe"))
        assertTrue(body.contains("riskFreeRate"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sharpe with custom risk_free_rate`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/sharpe?risk_free_rate=0.02")
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("0.02", json["riskFreeRate"]?.jsonPrimitive?.content)
    }

    @Test
    fun `correlation returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/correlation?ticker1=AAPL&ticker2=SPY&window=30")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("ticker1"))
        assertTrue(body.contains("ticker2"))
        assertTrue(body.contains("correlations"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["ticker1"]?.jsonPrimitive?.content)
        assertEquals("SPY", json["ticker2"]?.jsonPrimitive?.content)
    }

    @Test
    fun `volatility invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONG/volatility")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `beta missing target returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/beta?benchmark=SPY")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `correlation invalid window returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/correlation?ticker1=AAPL&ticker2=SPY&window=1")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sortino returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/sortino")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("sortino"))
        assertTrue(body.contains("riskFreeRate"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sortino invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONG/sortino")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `calmar returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/calmar")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("calmar"))
        assertTrue(body.contains("maxDrawdown"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `calmar invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONG/calmar")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `drawdown returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/drawdown")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("drawdown"))
        assertTrue(body.contains("maxDrawdown"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
    }

    @Test
    fun `drawdown invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONG/drawdown")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `treynor returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/treynor?benchmark=SPY")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("symbol"))
        assertTrue(body.contains("treynor"))
        assertTrue(body.contains("beta"))
        assertTrue(body.contains("riskFreeRate"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
        assertEquals("SPY", json["benchmark"]?.jsonPrimitive?.content)
    }

    @Test
    fun `treynor with custom risk_free_rate`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/treynor?benchmark=SPY&risk_free_rate=0.02")
        assertEquals(HttpStatusCode.OK, response.status)
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("0.02", json["riskFreeRate"]?.jsonPrimitive?.content)
    }

    @Test
    fun `treynor missing benchmark returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/treynor")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `treynor invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/TOOLONG/treynor?benchmark=SPY")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `information ratio returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/information-ratio?target=AAPL&benchmark=SPY")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("target"))
        assertTrue(body.contains("benchmark"))
        assertTrue(body.contains("informationRatio"))
        assertTrue(body.contains("trackingError"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["target"]?.jsonPrimitive?.content)
        assertEquals("SPY", json["benchmark"]?.jsonPrimitive?.content)
    }

    @Test
    fun `information ratio missing target returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/information-ratio?benchmark=SPY")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `information ratio missing benchmark returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/information-ratio?target=AAPL")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
