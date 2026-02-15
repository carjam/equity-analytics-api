package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DefaultsConfigTest {

    @Test
    fun `from returns defaults when meiken defaults config missing`() {
        val config = MapApplicationConfig()
        val result = DefaultsConfig.from(config)
        assertEquals(0.04, result.riskFreeRate)
        assertEquals(30, result.correlationWindow)
    }

    @Test
    fun `from reads values when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.defaults.riskFreeRate", "0.05")
        config.put("meiken.defaults.correlationWindow", "20")
        val result = DefaultsConfig.from(config)
        assertEquals(0.05, result.riskFreeRate)
        assertEquals(20, result.correlationWindow)
    }
}
