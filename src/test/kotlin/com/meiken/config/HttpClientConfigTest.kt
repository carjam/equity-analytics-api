package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HttpClientConfigTest {

    @Test
    fun `from returns defaults when meiken httpClient config missing`() {
        val config = MapApplicationConfig()
        val result = HttpClientConfig.from(config)
        assertEquals(60_000L, result.keepAliveTimeMs)
        assertEquals(50, result.maxConnectionsPerRoute)
    }

    @Test
    fun `from reads values when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.httpClient.keepAliveTimeMs", "120000")
        config.put("meiken.httpClient.maxConnectionsPerRoute", "100")
        val result = HttpClientConfig.from(config)
        assertEquals(120_000L, result.keepAliveTimeMs)
        assertEquals(100, result.maxConnectionsPerRoute)
    }
}
