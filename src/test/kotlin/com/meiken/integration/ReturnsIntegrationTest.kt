package com.meiken.integration

import com.meiken.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end integration test for the returns endpoint.
 * Uses full application with mock market data (no ALPHA_VANTAGE_API_KEY) and verifies JSON response structure.
 */
class ReturnsIntegrationTest {

    @Test
    fun `GET tickers AAPL returns returns 200 and valid Returns JSON structure`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        val json = Json.parseToJsonElement(body).jsonObject
        // Top-level Returns fields
        assertEquals("AAPL", json["symbol"]?.jsonPrimitive?.content)
        assertNotNull(json["fromDate"]?.jsonPrimitive?.content)
        assertNotNull(json["toDate"]?.jsonPrimitive?.content)
        // dailyReturns array
        val dailyReturns = json["dailyReturns"]?.jsonArray ?: throw AssertionError("missing dailyReturns")
        assertTrue(dailyReturns.size > 0, "dailyReturns should not be empty")
        val first = dailyReturns[0].jsonObject
        assertNotNull(first["date"]?.jsonPrimitive?.content)
        assertNotNull(first["returnValue"]?.jsonPrimitive?.content)
        // metadata
        val metadata = json["metadata"]?.jsonObject ?: throw AssertionError("missing metadata")
        assertNotNull(metadata["dataPoints"]?.jsonPrimitive?.content)
        assertNotNull(metadata["source"]?.jsonPrimitive?.content)
    }
}
