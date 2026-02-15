package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CalculationsConfigTest {

    @Test
    fun `from returns default when meiken calculations config missing`() {
        val config = MapApplicationConfig()
        val result = CalculationsConfig.from(config)
        assertEquals(252, result.tradingDaysPerYear)
    }

    @Test
    fun `from reads tradingDaysPerYear when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.calculations.tradingDaysPerYear", "250")
        val result = CalculationsConfig.from(config)
        assertEquals(250, result.tradingDaysPerYear)
    }
}
