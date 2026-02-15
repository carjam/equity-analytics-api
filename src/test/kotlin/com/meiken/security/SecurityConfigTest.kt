package com.meiken.security

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityConfigTest {

    @Test
    fun `from returns defaults when meiken security config missing`() {
        val config = MapApplicationConfig()
        val result = SecurityConfig.from(config)
        assertFalse(result.apiKeysEnabled)
        assertTrue(result.validApiKeys.isEmpty())
        assertFalse(result.requireHttps)
        assertTrue(result.allowedOrigins.isEmpty())
        assertEquals(100, result.rateLimitAnonymousPerMinute)
        assertEquals(1000, result.rateLimitAuthenticatedPerMinute)
        assertEquals(100, result.maxStringLength)
    }

    @Test
    fun `from reads security and rateLimit when present`() {
        val config = MapApplicationConfig()
        config.put("meiken.security.apiKeysEnabled", "true")
        config.put("meiken.security.validApiKeys", "key1, key2 , key3")
        config.put("meiken.security.requireHttps", "true")
        config.put("meiken.security.allowedOrigins", "https://app.example.com")
        config.put("meiken.security.maxStringLength", "200")
        config.put("meiken.rateLimit.requestsPerMinute", "50")
        config.put("meiken.rateLimit.authenticatedPerMinute", "500")
        val result = SecurityConfig.from(config)
        assertTrue(result.apiKeysEnabled)
        assertEquals(setOf("key1", "key2", "key3"), result.validApiKeys)
        assertTrue(result.requireHttps)
        assertEquals(listOf("https://app.example.com"), result.allowedOrigins)
        assertEquals(200, result.maxStringLength)
        assertEquals(50, result.rateLimitAnonymousPerMinute)
        assertEquals(500, result.rateLimitAuthenticatedPerMinute)
    }

    @Test
    fun `from uses security defaults when rateLimit section missing`() {
        val config = MapApplicationConfig()
        config.put("meiken.security.maxStringLength", "50")
        val result = SecurityConfig.from(config)
        assertEquals(50, result.maxStringLength)
        assertEquals(100, result.rateLimitAnonymousPerMinute)
        assertEquals(1000, result.rateLimitAuthenticatedPerMinute)
    }
}
