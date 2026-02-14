package com.meiken.api

import com.meiken.error.BadRequestException
import com.meiken.service.AnalyticsService
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
                    val symbol = call.parameters["symbol"]?.uppercase() ?: ""
                    if (!symbol.matches(SYMBOL_REGEX)) {
                        throw BadRequestException("Invalid symbol: must be 1-5 alphanumeric characters")
                    }
                    val fromDate = call.request.queryParameters["from_date"]?.let { parseDate(it) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { parseDate(it) }
                        ?: getToday()
                    val response = analyticsService.calculateVolatility(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("sharpe") {
                get {
                    val symbol = call.parameters["symbol"]?.uppercase() ?: ""
                    if (!symbol.matches(SYMBOL_REGEX)) {
                        throw BadRequestException("Invalid symbol: must be 1-5 alphanumeric characters")
                    }
                    val riskFreeRate = call.request.queryParameters["risk_free_rate"]?.toDoubleOrNull()
                        ?: DEFAULT_RISK_FREE_RATE
                    val fromDate = call.request.queryParameters["from_date"]?.let { parseDate(it) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { parseDate(it) }
                        ?: getToday()
                    val response = analyticsService.calculateSharpe(symbol, fromDate, toDate, riskFreeRate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
    route("beta") {
        get {
            val target = call.request.queryParameters["target"]?.uppercase()
            val benchmark = call.request.queryParameters["benchmark"]?.uppercase()
            if (target.isNullOrBlank() || !target.matches(SYMBOL_REGEX)) {
                throw BadRequestException("target is required and must be 1-5 alphanumeric characters")
            }
            if (benchmark.isNullOrBlank() || !benchmark.matches(SYMBOL_REGEX)) {
                throw BadRequestException("benchmark is required and must be 1-5 alphanumeric characters")
            }
            val fromDate = call.request.queryParameters["from_date"]?.let { parseDate(it) }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { parseDate(it) }
                ?: getToday()
            val response = analyticsService.calculateBeta(target, benchmark, fromDate, toDate)
            call.respond(HttpStatusCode.OK, response)
        }
    }
    route("correlation") {
        get {
            val ticker1 = call.request.queryParameters["ticker1"]?.uppercase()
            val ticker2 = call.request.queryParameters["ticker2"]?.uppercase()
            if (ticker1.isNullOrBlank() || !ticker1.matches(SYMBOL_REGEX)) {
                throw BadRequestException("ticker1 is required and must be 1-5 alphanumeric characters")
            }
            if (ticker2.isNullOrBlank() || !ticker2.matches(SYMBOL_REGEX)) {
                throw BadRequestException("ticker2 is required and must be 1-5 alphanumeric characters")
            }
            val fromDate = call.request.queryParameters["from_date"]?.let { parseDate(it) }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { parseDate(it) }
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
