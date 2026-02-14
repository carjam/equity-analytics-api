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

class AlphaRoutesTest {

    @Test
    fun `successful alpha calculation returns 200 with valid JSON`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha?target=AAPL&benchmark=SPY")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("target"))
        assertTrue(body.contains("benchmark"))
        assertTrue(body.contains("alpha"))
        assertTrue(body.contains("metadata"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("AAPL", json["target"]?.jsonPrimitive?.content)
        assertEquals("SPY", json["benchmark"]?.jsonPrimitive?.content)
    }

    @Test
    fun `missing target returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha?benchmark=SPY")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("BAD_REQUEST") || body.contains("target"))
    }

    @Test
    fun `missing benchmark returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha?target=AAPL")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("BAD_REQUEST") || body.contains("benchmark"))
    }

    @Test
    fun `invalid date format returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=bad-date")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `date range over 365 days returns 400`() = testApplication {
        environment { config = MapApplicationConfig() }
        application { module() }
        val response = client.get("/api/v1/alpha?target=AAPL&benchmark=SPY&from_date=2020-01-01&to_date=2021-06-01")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("INVALID_DATE_RANGE") || body.contains("exceed"))
    }
}
