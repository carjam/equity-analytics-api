package com.meiken.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.Route

fun Route.returnsRoutes() {
    route("tickers") {
        route("{symbol}") {
            route("returns") {
                get {
                    call.respondText("Not implemented yet", status = HttpStatusCode.NotImplemented)
                }
            }
        }
    }
}
