package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DateRangesConfigTest {

    @Test
    fun `from returns default when meiken dateRanges config missing`() {
        val config = MapApplicationConfig()
        val result = DateRangesConfig.from(config)
        assertEquals(365, result.maxDays)
    }

    @Test
    fun `from reads maxDays when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.dateRanges.maxDays", "500")
        val result = DateRangesConfig.from(config)
        assertEquals(500, result.maxDays)
    }
}
