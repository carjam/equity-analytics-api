package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DataQualityConfigTest {

    @Test
    fun `from returns defaults when meiken dataQuality config missing`() {
        val config = MapApplicationConfig()
        val result = DataQualityConfig.from(config)
        assertEquals(0.01, result.minPrice)
        assertEquals(1_000_000.0, result.maxPrice)
        assertEquals(0.50, result.maxSingleDayChangePct)
        assertEquals(3.0, result.outlierSigma)
    }

    @Test
    fun `from reads values when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.dataQuality.minPrice", "0.05")
        config.put("meiken.dataQuality.maxPrice", "500000")
        config.put("meiken.dataQuality.maxSingleDayChangePct", "0.25")
        config.put("meiken.dataQuality.outlierSigma", "2.5")
        val result = DataQualityConfig.from(config)
        assertEquals(0.05, result.minPrice)
        assertEquals(500_000.0, result.maxPrice)
        assertEquals(0.25, result.maxSingleDayChangePct)
        assertEquals(2.5, result.outlierSigma)
    }
}
