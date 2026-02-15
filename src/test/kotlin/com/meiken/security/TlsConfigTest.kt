package com.meiken.security

import io.ktor.server.config.MapApplicationConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TlsConfigTest {

    @Test
    fun `from returns nulls when ktor security ssl config missing`() {
        val config = MapApplicationConfig()
        val result = TlsConfig.from(config)
        assertEquals(null, result.keyStorePath)
        assertEquals(null, result.keyStorePassword)
        assertEquals(null, result.keyAlias)
        assertFalse(result.isConfigured)
    }

    @Test
    fun `from reads keyStore keyStorePassword keyAlias when present`() {
        val config = MapApplicationConfig()
        config.put("ktor.security.ssl.keyStore", "/path/to/keystore.jks")
        config.put("ktor.security.ssl.keyStorePassword", "secret")
        config.put("ktor.security.ssl.keyAlias", "myalias")
        val result = TlsConfig.from(config)
        assertEquals("/path/to/keystore.jks", result.keyStorePath)
        assertEquals("secret", result.keyStorePassword)
        assertEquals("myalias", result.keyAlias)
        assertTrue(result.isConfigured)
    }

    @Test
    fun `isConfigured is false when keyStorePath is blank`() {
        val config = MapApplicationConfig()
        config.put("ktor.security.ssl.keyStore", "   ")
        config.put("ktor.security.ssl.keyStorePassword", "secret")
        val result = TlsConfig.from(config)
        assertFalse(result.isConfigured)
    }
}
