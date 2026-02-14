package com.meiken.security

import com.meiken.observability.Metrics
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory

/**
 * Manages API keys: validation, optional expiration, usage logging, and metrics.
 * Supports multiple valid keys simultaneously for rotation; keys can have expiration dates.
 * Call [recordUsage] on successful auth and [recordFailure] on invalid/missing key.
 */
class ApiKeyManager(
    private val validKeys: Set<String>,
    private val keyExpirations: Map<String, Instant> = emptyMap()
) {
    private val log = LoggerFactory.getLogger(ApiKeyManager::class.java)

    /**
     * Validates the given key: must be in [validKeys] and not expired (if expiration is set).
     * Returns a key identifier for principal/metrics, or null if invalid.
     */
    fun validate(key: String?): ApiKeyPrincipal? {
        if (key.isNullOrBlank()) return null
        val trimmed = key.trim()
        if (trimmed !in validKeys) {
            recordFailure()
            return null
        }
        keyExpirations[trimmed]?.let { expires ->
            val now = Clock.System.now()
            if (now > expires) {
                log.warn("API key expired (key_id={})", keyIdForLog(trimmed))
                recordFailure()
                return null
            }
        }
        val keyId = keyIdForLog(trimmed)
        return ApiKeyPrincipal(keyId, trimmed)
    }

    fun recordUsage(keyId: String, endpoint: String) {
        Metrics.recordApiKeyUsage(keyId, endpoint)
        log.debug("API key usage: key_id={} endpoint={}", keyId, endpoint)
    }

    fun recordFailure() {
        Metrics.recordApiKeyAuthFailure()
    }

    fun isValidKey(key: String): Boolean = key.trim() in validKeys

    private fun keyIdForLog(key: String): String =
        if (key.length <= 8) "***" else "${key.take(4)}...${key.takeLast(4)}"
}

/**
 * Principal for API key authentication; [keyId] is a masked identifier for logging/metrics.
 */
data class ApiKeyPrincipal(val keyId: String, val key: String)
