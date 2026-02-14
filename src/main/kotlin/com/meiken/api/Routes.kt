package com.meiken.api

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.http.HttpStatusCode

fun Application.configureRouting() {
    routing {
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }
        route("api/v1") {
            returnsRoutes()
            alphaRoutes()
        }
    }
}
