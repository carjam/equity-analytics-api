package com.meiken.api

import com.meiken.config.DefaultsConfig
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

/**
 * Analytics routes under /api/v1. All metrics use close-of-day prices only (close-to-close daily returns).
 * - GET tickers/{symbol}/volatility (optional from_date, to_date; default YTD)
 * - GET tickers/{symbol}/sharpe (optional risk_free_rate, from_date, to_date; default risk_free_rate from config)
 * - GET tickers/{symbol}/sortino (optional risk_free_rate, from_date, to_date; default risk_free_rate from config)
 * - GET tickers/{symbol}/calmar (optional from_date, to_date; default YTD)
 * - GET tickers/{symbol}/momentum (optional from_date, to_date, lookback; default lookback=20; supports comma-separated list)
 * - GET tickers/{symbol}/moving-averages (optional from_date, to_date, window; default window=20,50,200; supports comma-separated list)
 * - GET tickers/{symbol}/price-levels (optional from_date, to_date; default YTD; 52-week high/low)
 * - GET tickers/{symbol}/z-score (optional from_date, to_date, window; default window=60)
 * - GET tickers/{symbol}/drawdown (optional from_date, to_date; default YTD)
 * - GET beta?target=&benchmark= (optional from_date, to_date)
 * - GET relative-strength?target=&benchmark= (optional from_date, to_date)
 * - GET correlation?ticker1=&ticker2= (optional from_date, to_date, window; default window from config)
 * Symbols 1-5 alphanumeric; dates YYYY-MM-DD; errors -> 400/404/500.
 */
fun Route.analyticsRoutes(
    analyticsService: AnalyticsService,
    defaultsConfig: DefaultsConfig? = null,
    maxStringLength: Int = 100
) {
    val riskFreeRateDefault = defaultsConfig?.riskFreeRate ?: 0.04
    val correlationWindowDefault = defaultsConfig?.correlationWindow ?: 30
    route("tickers") {
        route("{symbol}") {
            route("volatility") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculateVolatility(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("sharpe") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val riskFreeRate = call.request.queryParameters["risk_free_rate"]?.toDoubleOrNull()
                        ?: riskFreeRateDefault
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculateSharpe(symbol, fromDate, toDate, riskFreeRate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("sortino") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val riskFreeRate = call.request.queryParameters["risk_free_rate"]?.toDoubleOrNull()
                        ?: riskFreeRateDefault
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculateSortino(symbol, fromDate, toDate, riskFreeRate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("calmar") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculateCalmar(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("momentum") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    
                    // Parse lookback periods (comma-separated string, default 20)
                    val lookbacksStr = call.request.queryParameters["lookback"] ?: "20"
                    val lookbacks = try {
                        lookbacksStr.split(",").map { it.trim().toInt() }
                    } catch (e: NumberFormatException) {
                        throw BadRequestException("Invalid lookback format. Use comma-separated integers (e.g., '20' or '20,60,120')")
                    }
                    
                    if (lookbacks.any { it <= 0 || it > 252 }) {
                        throw BadRequestException("Lookback periods must be between 1 and 252 trading days")
                    }
                    
                    val response = analyticsService.calculateMomentum(symbol, fromDate, toDate, lookbacks)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("moving-averages") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    
                    // Parse window sizes (comma-separated string, default 20,50,200)
                    val windowsStr = call.request.queryParameters["window"] ?: "20,50,200"
                    val windows = try {
                        windowsStr.split(",").map { it.trim().toInt() }
                    } catch (e: NumberFormatException) {
                        throw BadRequestException("Invalid window format. Use comma-separated integers (e.g., '20' or '20,50,200')")
                    }
                    
                    if (windows.any { it <= 0 || it > 252 }) {
                        throw BadRequestException("Window sizes must be between 1 and 252 trading days")
                    }
                    
                    val response = analyticsService.calculateMovingAverages(symbol, fromDate, toDate, windows)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("price-levels") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculatePriceLevels(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("z-score") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val window = call.request.queryParameters["window"]?.toIntOrNull() ?: 60
                    if (window < 2 || window > 252) {
                        throw BadRequestException("Window must be between 2 and 252 trading days")
                    }
                    val response = analyticsService.calculateZScore(symbol, fromDate, toDate, window)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
            route("drawdown") {
                get {
                    val symbol = InputValidator.validateSymbol(call.parameters["symbol"], maxLength = maxStringLength)
                    val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                        ?: getCurrentYearStart()
                    val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                        ?: getToday()
                    val response = analyticsService.calculateDrawdown(symbol, fromDate, toDate)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
        route("beta") {
            get {
                val target = InputValidator.validateSymbol(call.request.queryParameters["target"], "target", maxLength = maxStringLength)
                val benchmark = InputValidator.validateSymbol(call.request.queryParameters["benchmark"], "benchmark", maxLength = maxStringLength)
                val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                    ?: getCurrentYearStart()
                val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                    ?: getToday()
                val response = analyticsService.calculateBeta(target, benchmark, fromDate, toDate)
                call.respond(HttpStatusCode.OK, response)
            }
        }
        route("relative-strength") {
            get {
                val target = InputValidator.validateSymbol(call.request.queryParameters["target"], "target", maxLength = maxStringLength)
                val benchmark = InputValidator.validateSymbol(call.request.queryParameters["benchmark"], "benchmark", maxLength = maxStringLength)
                val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                    ?: getCurrentYearStart()
                val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                    ?: getToday()
                val response = analyticsService.calculateRelativeStrength(target, benchmark, fromDate, toDate)
                call.respond(HttpStatusCode.OK, response)
            }
        }
        route("correlation") {
        get {
            val ticker1 = InputValidator.validateSymbol(call.request.queryParameters["ticker1"], "ticker1", maxLength = maxStringLength)
            val ticker2 = InputValidator.validateSymbol(call.request.queryParameters["ticker2"], "ticker2", maxLength = maxStringLength)
            val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                ?: getToday()
            val window = call.request.queryParameters["window"]?.toIntOrNull() ?: correlationWindowDefault
            if (window < 2) {
                throw BadRequestException("window must be at least 2")
            }
            val response = analyticsService.calculateCorrelation(ticker1, ticker2, fromDate, toDate, window)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
