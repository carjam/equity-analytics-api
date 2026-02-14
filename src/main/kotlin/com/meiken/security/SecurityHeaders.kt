package com.meiken.security

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.ApplicationSendPipeline

/**
 * Plugin that adds security headers to every response.
 * - X-Content-Type-Options: nosniff
 * - X-Frame-Options: DENY
 * - X-XSS-Protection: 1; mode=block
 * - Content-Security-Policy: default-src 'none'
 * - Strict-Transport-Security (when [addHsts] is true, e.g. HTTPS)
 */
fun createSecurityHeadersPlugin(addHsts: Boolean = false) = createApplicationPlugin(name = "SecurityHeaders") {
    onCall { call ->
        call.response.pipeline.intercept(ApplicationSendPipeline.Before) {
            call.response.headers.append("X-Content-Type-Options", "nosniff")
            call.response.headers.append("X-Frame-Options", "DENY")
            call.response.headers.append("X-XSS-Protection", "1; mode=block")
            call.response.headers.append("Content-Security-Policy", "default-src 'none'")
            if (addHsts) {
                call.response.headers.append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            }
            proceed()
        }
    }
}
