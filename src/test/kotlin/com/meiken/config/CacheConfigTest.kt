package com.meiken.config

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CacheConfigTest {

    @Test
    fun `from returns defaults when meiken cache config missing`() {
        val config = MapApplicationConfig()
        val result = CacheConfig.from(config)
        assertEquals(3600L, result.ttlSeconds)
        assertEquals(1000, result.maxSize)
        assertEquals(86400L, result.staleSeconds)
        assertEquals(3600L, result.oneHourSeconds)
        assertEquals(5, result.gapThreshold)
        assertEquals(5, result.recentDatesDays)
        assertEquals(0.5, result.sparseRatio)
    }

    @Test
    fun `from reads values from config when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.cache.ttl", "7200")
        config.put("meiken.cache.maxSize", "500")
        config.put("meiken.cache.staleSeconds", "43200")
        config.put("meiken.cache.oneHourSeconds", "3600")
        config.put("meiken.cache.gapThreshold", "3")
        config.put("meiken.cache.recentDatesDays", "7")
        config.put("meiken.cache.sparseRatio", "0.6")
        val result = CacheConfig.from(config)
        assertEquals(7200L, result.ttlSeconds)
        assertEquals(500, result.maxSize)
        assertEquals(43200L, result.staleSeconds)
        assertEquals(3600L, result.oneHourSeconds)
        assertEquals(3, result.gapThreshold)
        assertEquals(7, result.recentDatesDays)
        assertEquals(0.6, result.sparseRatio)
    }

    @Test
    fun `from uses default for missing optional properties`() {
        val config = MapApplicationConfig()
        config.put("meiken.cache.ttl", "100")
        val result = CacheConfig.from(config)
        assertEquals(100L, result.ttlSeconds)
        assertEquals(1000, result.maxSize)
        assertEquals(5, result.recentDatesDays)
    }
}
