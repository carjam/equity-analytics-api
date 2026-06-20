package com.meiken.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private const val LEGAL_INDEX = """
# Legal

- [Terms of Service](/legal/terms)
- [Privacy Policy](/legal/privacy)
- [Notice](/legal/notice)

Source: https://github.com/carjam/equity-analytics-api
"""

/**
 * Serves bundled legal documents from classpath `legal/` (copied from docs/ at build time).
 */
fun Route.legalRoutes() {
    get("/legal") {
        call.response.headers.append("Cache-Control", "public, max-age=86400")
        call.respondText(LEGAL_INDEX.trim(), ContentType("text", "markdown"), HttpStatusCode.OK)
    }
    get("/legal/terms") {
        call.respondLegalDocument("TERMS_OF_SERVICE.md")
    }
    get("/legal/privacy") {
        call.respondLegalDocument("PRIVACY_POLICY.md")
    }
    get("/legal/notice") {
        call.respondLegalDocument("NOTICE.md")
    }
}

private object LegalResources

private suspend fun io.ktor.server.application.ApplicationCall.respondLegalDocument(filename: String) {
    response.headers.append("Cache-Control", "public, max-age=86400")
    val body = loadLegalResource(filename)
    respondText(body, ContentType("text", "markdown"), HttpStatusCode.OK)
}

private fun loadLegalResource(filename: String): String {
    val stream = LegalResources::class.java.classLoader.getResourceAsStream("legal/$filename")
        ?: error("Missing legal resource: legal/$filename")
    return stream.bufferedReader().use { it.readText() }
}
