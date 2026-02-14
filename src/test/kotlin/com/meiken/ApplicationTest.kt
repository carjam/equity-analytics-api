package com.meiken

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun `server starts and health endpoint returns OK`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        kotlin.test.assertTrue(body.contains("ok"), "Expected health body to contain 'ok', got: $body")
        kotlin.test.assertTrue(body.contains("Service is operational"), "Expected health body to contain message, got: $body")
        kotlin.test.assertTrue(body.contains("dependencies"), "Expected health body to include dependencies, got: $body")
    }

    @Test
    fun `returns endpoint returns 200 with JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        kotlin.test.assertTrue(body.contains("AAPL"), "Expected body to contain symbol, got: $body")
        kotlin.test.assertTrue(body.contains("dailyReturns"), "Expected body to contain dailyReturns, got: $body")
    }

    @Test
    fun `alpha endpoint without params returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
