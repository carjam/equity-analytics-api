package com.meiken.security

import io.ktor.server.config.ApplicationConfig

/**
 * Security configuration read from application.conf (meiken.security).
 * API keys disabled by default for development; enable with API_KEYS_ENABLED=true or validApiKeys set.
 */
data class SecurityConfig(
    val apiKeysEnabled: Boolean,
    val validApiKeys: Set<String>,
    val requireHttps: Boolean,
    val allowedOrigins: List<String>,
    val rateLimitAnonymousPerMinute: Int,
    val rateLimitAuthenticatedPerMinute: Int,
    val maxStringLength: Int
) {
    companion object {
        fun from(config: ApplicationConfig): SecurityConfig {
            val security = try {
                config.config("meiken").config("security")
            } catch (_: Exception) {
                return SecurityConfig(
                    apiKeysEnabled = false,
                    validApiKeys = emptySet(),
                    requireHttps = false,
                    allowedOrigins = emptyList(),
                    rateLimitAnonymousPerMinute = 100,
                    rateLimitAuthenticatedPerMinute = 1000,
                    maxStringLength = 100
                )
            }
            val apiKeysEnabled = security.propertyOrNull("apiKeysEnabled")?.getString()?.toBooleanStrictOrNull() ?: false
            val validApiKeysStr = security.propertyOrNull("validApiKeys")?.getString()
            val validApiKeys = validApiKeysStr
                ?.split(",")
                ?.map { s -> s.trim() }
                ?.filter { s -> s.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            val requireHttps = security.propertyOrNull("requireHttps")?.getString()?.toBooleanStrictOrNull() ?: false
            val allowedOriginsStr = security.propertyOrNull("allowedOrigins")?.getString()
            val allowedOrigins = allowedOriginsStr?.split(",")?.map { s -> s.trim() }?.filter { s -> s.isNotEmpty() } ?: emptyList()

            val rateLimit = try {
                config.config("meiken").config("rateLimit")
            } catch (_: Exception) {
                null
            }
            val anonymousPerMinute = rateLimit?.propertyOrNull("requestsPerMinute")?.getString()?.toIntOrNull() ?: 100
            val authenticatedPerMinute = rateLimit?.propertyOrNull("authenticatedPerMinute")?.getString()?.toIntOrNull() ?: 1000
            val maxStringLength = security.propertyOrNull("maxStringLength")?.getString()?.toIntOrNull() ?: 100

            return SecurityConfig(
                apiKeysEnabled = apiKeysEnabled,
                validApiKeys = validApiKeys,
                requireHttps = requireHttps,
                allowedOrigins = allowedOrigins,
                rateLimitAnonymousPerMinute = anonymousPerMinute,
                rateLimitAuthenticatedPerMinute = authenticatedPerMinute,
                maxStringLength = maxStringLength
            )
        }
    }
}
