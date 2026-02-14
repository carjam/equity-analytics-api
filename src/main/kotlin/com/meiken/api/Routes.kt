package com.meiken.api

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

private val healthJson = Json { encodeDefaults = true }

fun Application.configureRouting() {
    routing {
        get("/health") {
            val dependencies = listOf(
                DependencyStatus(name = "app", status = "up", message = "Application is running")
                // Add more as you wire them: DB, Alpha Vantage API, cache, etc.
            )
            val response = HealthResponse(
                status = "ok",
                message = "Service is operational",
                timestamp = Instant.now().toString(),
                dependencies = dependencies
            )
            val body = healthJson.encodeToString(response)
            call.respondText(body, ContentType.Application.Json, HttpStatusCode.OK)
        }
        route("api/v1") {
            returnsRoutes()
            alphaRoutes()
        }
    }
}
