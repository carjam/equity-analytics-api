package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerformanceConfigTest {

    @Test
    fun `from returns default compression when meiken performance config missing`() {
        val config = MapApplicationConfig()
        val result = PerformanceConfig.from(config)
        assertTrue(result.compression.enabled)
        assertEquals(1024, result.compression.minSize)
        assertEquals(6, result.compression.level)
    }

    @Test
    fun `from reads compression when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.performance.compression.enabled", "false")
        config.put("meiken.performance.compression.minSize", "2048")
        config.put("meiken.performance.compression.level", "9")
        val result = PerformanceConfig.from(config)
        assertFalse(result.compression.enabled)
        assertEquals(2048, result.compression.minSize)
        assertEquals(9, result.compression.level)
    }
}
