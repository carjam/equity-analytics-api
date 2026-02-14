package com.meiken.api

import com.meiken.service.ReturnsService
import com.meiken.security.InputValidator
import com.meiken.util.getCurrentYearStart
import com.meiken.util.getToday
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * GET /api/v1/tickers/{symbol}/returns
 * Returns daily close-to-close returns (from close-of-day prices only). Path: symbol (1-5 alphanumeric, uppercased).
 * Query: from_date, to_date (optional, YYYY-MM-DD). If dates omitted, uses YTD. Returns 200 with JSON [Returns] or 400/404/500.
 */
fun Route.returnsRoutes(returnsService: ReturnsService) {
    route("tickers") {
        route("{symbol}") {
            route("returns") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"])
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                        ?: getToday()
                    val returns = returnsService.calculateReturns(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, returns)
                }
            }
        }
    }
}
