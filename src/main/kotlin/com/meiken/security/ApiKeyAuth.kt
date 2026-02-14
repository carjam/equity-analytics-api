package com.meiken.security

import com.meiken.error.ErrorDetail
import com.meiken.error.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey

/** Attribute key for the API key principal (set when auth succeeds). */
val ApiKeyPrincipalKey = AttributeKey<ApiKeyPrincipal>("ApiKeyPrincipal")

private const val HEADER_API_KEY = "X-API-Key"
private const val QUERY_API_KEY = "api_key"

/**
 * Extracts API key from request: X-API-Key header or api_key query parameter.
 */
fun ApplicationCall.apiKeyFromRequest(): String? =
    request.header(HEADER_API_KEY) ?: request.queryParameters[QUERY_API_KEY]

/**
 * Validates API key using [apiKeyManager]. If valid, puts [ApiKeyPrincipal] in [ApplicationCall.attributes] and returns true.
 * If invalid or missing, responds with 401 and error body and returns false (caller should not proceed).
 */
suspend fun ApplicationCall.validateApiKey(apiKeyManager: ApiKeyManager): Boolean {
    val key = apiKeyFromRequest()
    val principal = apiKeyManager.validate(key)
    if (principal == null) {
        apiKeyManager.recordFailure()
        respond(HttpStatusCode.Unauthorized, ErrorResponse(ErrorDetail("UNAUTHORIZED", "Missing or invalid API key. Use X-API-Key header or api_key query parameter.")))
        return false
    }
    attributes.put(ApiKeyPrincipalKey, principal)
    return true
}

/**
 * Gets the [ApiKeyPrincipal] from the call if previously set by [validateApiKey].
 */
fun ApplicationCall.apiKeyPrincipal(): ApiKeyPrincipal? = attributes.getOrNull(ApiKeyPrincipalKey)
