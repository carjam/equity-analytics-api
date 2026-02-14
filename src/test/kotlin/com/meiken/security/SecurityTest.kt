package com.meiken.security

import com.meiken.module
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Security tests: API key auth (when enabled), input validation.
 * Uses MapApplicationConfig; when meiken.security is missing, API keys are disabled (default).
 */
class SecurityTest {

    @Test
    fun `when API keys disabled, api v1 returns 200 without key`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `when API keys enabled, api v1 without key returns 401`() = testApplication {
        val config = MapApplicationConfig()
        config.put("meiken.security.apiKeysEnabled", "true")
        config.put("meiken.security.validApiKeys", "test-key-123")
        environment { this.config = config }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("UNAUTHORIZED") || body.contains("API key"), "Expected auth error in body: $body")
    }

    @Test
    fun `when API keys enabled, api v1 with valid X-API-Key returns 200`() = testApplication {
        val config = MapApplicationConfig()
        config.put("meiken.security.apiKeysEnabled", "true")
        config.put("meiken.security.validApiKeys", "test-key-123")
        environment { this.config = config }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns") {
            header("X-API-Key", "test-key-123")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `when API keys enabled, api v1 with valid api_key query returns 200`() = testApplication {
        val config = MapApplicationConfig()
        config.put("meiken.security.apiKeysEnabled", "true")
        config.put("meiken.security.validApiKeys", "test-key-query")
        environment { this.config = config }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns") {
            parameter("api_key", "test-key-query")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `invalid symbol returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/INVALID_SYMBOL_LONG/returns")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `future from_date returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns") {
            parameter("from_date", "2030-01-01")
            parameter("to_date", "2030-12-31")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `health and metrics are open without API key even when API keys enabled`() = testApplication {
        val config = MapApplicationConfig()
        config.put("meiken.security.apiKeysEnabled", "true")
        config.put("meiken.security.validApiKeys", "key1")
        environment { this.config = config }
        application { module() }
        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        val metrics = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, metrics.status)
    }
}
