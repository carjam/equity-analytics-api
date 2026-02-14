package com.meiken.api

import com.meiken.error.BadRequestException
import com.meiken.service.ReturnsService
import com.meiken.util.getCurrentYearStart
import com.meiken.util.getToday
import com.meiken.util.parseDate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private val SYMBOL_REGEX = Regex("^[A-Z0-9]{1,5}$")

fun Route.returnsRoutes(returnsService: ReturnsService) {
    route("tickers") {
        route("{symbol}") {
            route("returns") {
                get {
                    val symbol = call.parameters["symbol"]?.uppercase() ?: ""
                    if (!symbol.matches(SYMBOL_REGEX)) {
                        throw BadRequestException("Invalid symbol: must be 1-5 alphanumeric characters")
                    }
                    val fromDate = call.request.queryParameters["from_date"]?.let { parseDate(it) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { parseDate(it) }
                        ?: getToday()
                    val returns = returnsService.calculateReturns(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, returns)
                }
            }
        }
    }
}
