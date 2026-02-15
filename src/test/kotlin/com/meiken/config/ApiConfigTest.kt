package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApiConfigTest {

    @Test
    fun `from returns default when meiken api config missing`() {
        val config = MapApplicationConfig()
        val result = ApiConfig.from(config)
        assertEquals(300, result.cacheControlMaxAgeSeconds)
    }

    @Test
    fun `from reads cacheControlMaxAgeSeconds when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.api.cacheControlMaxAgeSeconds", "600")
        val result = ApiConfig.from(config)
        assertEquals(600, result.cacheControlMaxAgeSeconds)
    }
}
