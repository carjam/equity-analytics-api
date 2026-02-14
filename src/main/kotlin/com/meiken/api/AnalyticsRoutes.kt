package com.meiken.api

import com.meiken.error.BadRequestException
import com.meiken.security.InputValidator
import com.meiken.service.AnalyticsService
import com.meiken.util.getCurrentYearStart
import com.meiken.util.getToday
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
private const val DEFAULT_RISK_FREE_RATE = 0.04
private const val DEFAULT_CORRELATION_WINDOW = 30

/**
 * Analytics routes under /api/v1. All metrics use close-of-day prices only (close-to-close daily returns).
 * - GET tickers/{symbol}/volatility (optional from_date, to_date; default YTD)
 * - GET tickers/{symbol}/sharpe (optional risk_free_rate=0.04, from_date, to_date)
 * - GET beta?target=&benchmark= (optional from_date, to_date)
 * - GET correlation?ticker1=&ticker2= (optional from_date, to_date, window=30)
 * Symbols 1-5 alphanumeric; dates YYYY-MM-DD; errors -> 400/404/500.
 */
fun Route.analyticsRoutes(analyticsService: AnalyticsService) {
    route("tickers") {
        route("{symbol}") {
            route("volatility") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"])
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                        ?: getToday()
                    val response = analyticsService.calculateVolatility(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("sharpe") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"])
                    val riskFreeRate = call.request.queryParameters["risk_free_rate"]?.toDoubleOrNull()
                        ?: DEFAULT_RISK_FREE_RATE
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                        ?: getToday()
                    val response = analyticsService.calculateSharpe(symbol, fromDate, toDate, riskFreeRate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
    route("beta") {
        get {
            val target = InputValidator.validateSymbol(call.request.queryParameters["target"], "target")
            val benchmark = InputValidator.validateSymbol(call.request.queryParameters["benchmark"], "benchmark")
            val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                ?: getToday()
            val response = analyticsService.calculateBeta(target, benchmark, fromDate, toDate)
            call.respond(HttpStatusCode.OK, response)
        }
    }
    route("correlation") {
        get {
            val ticker1 = InputValidator.validateSymbol(call.request.queryParameters["ticker1"], "ticker1")
            val ticker2 = InputValidator.validateSymbol(call.request.queryParameters["ticker2"], "ticker2")
            val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                ?: getToday()
            val window = call.request.queryParameters["window"]?.toIntOrNull() ?: DEFAULT_CORRELATION_WINDOW
            if (window < 2) {
                throw BadRequestException("window must be at least 2")
            }
            val response = analyticsService.calculateCorrelation(ticker1, ticker2, fromDate, toDate, window)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
