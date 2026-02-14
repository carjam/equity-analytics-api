package com.meiken.security

import io.ktor.server.config.ApplicationConfig

/**
 * SSL/TLS configuration read from application.conf (ktor.security.ssl).
 * In production, TLS is typically terminated at a reverse proxy (nginx, ALB); set keyStore
 * only when running Ktor with HTTPS directly.
 */
data class TlsConfig(
    val keyStorePath: String?,
    val keyStorePassword: String?,
    val keyAlias: String?
) {
    val isConfigured: Boolean
        get() = !keyStorePath.isNullOrBlank() && !keyStorePassword.isNullOrBlank()

    companion object {
        fun from(config: ApplicationConfig): TlsConfig {
            val ssl = try {
                config.config("ktor").config("security").config("ssl")
            } catch (_: Exception) {
                return TlsConfig(null, null, null)
            }
            return TlsConfig(
                keyStorePath = ssl.propertyOrNull("keyStore")?.getString(),
                keyStorePassword = ssl.propertyOrNull("keyStorePassword")?.getString(),
                keyAlias = ssl.propertyOrNull("keyAlias")?.getString()
            )
        }
    }
}
