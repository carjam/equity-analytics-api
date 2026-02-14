package com.meiken

import com.meiken.api.configureRouting
import com.meiken.error.BadRequestException
import com.meiken.error.ErrorDetail
import com.meiken.error.ErrorResponse
import com.meiken.error.ExternalServiceException
import com.meiken.error.InvalidDateRangeException
import com.meiken.error.SymbolNotFoundException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.json.Json

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }

    install(StatusPages) {
        exception<InvalidDateRangeException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("INVALID_DATE_RANGE", cause.message ?: "Invalid date range"))
            )
        }
        exception<SymbolNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(ErrorDetail("SYMBOL_NOT_FOUND", cause.message ?: "Symbol not found"))
            )
        }
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(ErrorDetail("BAD_REQUEST", cause.message ?: "Bad request"))
            )
        }
        exception<ExternalServiceException> { call, cause ->
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(ErrorDetail("EXTERNAL_SERVICE_ERROR", cause.message ?: "External service error"))
            )
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(ErrorDetail("INTERNAL_ERROR", cause.message ?: "Internal server error"))
            )
        }
    }

    install(CallLogging)

    install(CORS) {
        anyHost()
    }

    configureRouting()
}
