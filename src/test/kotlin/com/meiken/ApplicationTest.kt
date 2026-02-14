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
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun `returns stub returns 501`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/tickers/AAPL/returns")
        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertEquals("Not implemented yet", response.bodyAsText())
    }

    @Test
    fun `alpha stub returns 501`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha")
        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertEquals("Not implemented yet", response.bodyAsText())
    }
}
