package com.meiken.api

import com.meiken.config.DefaultsConfig
import com.meiken.security.InputValidator
import com.meiken.service.AlphaService
import com.meiken.util.getCurrentYearStart
import com.meiken.util.getToday
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * GET /api/v1/alpha
 * Jensen's alpha (OLS regression intercept of excess returns) vs benchmark from close-of-day returns.
 * Query: target, benchmark (required); from_date, to_date, risk_free_rate (optional; defaults from config).
 * Returns 200 with JSON [Alpha] or 400/404/500.
 */
fun Route.alphaRoutes(alphaService: AlphaService, defaultsConfig: DefaultsConfig? = null, maxStringLength: Int = 100) {
    val riskFreeRateDefault = defaultsConfig?.riskFreeRate ?: 0.04
    route("alpha") {
        get {
            val target = InputValidator.validateSymbol(call.request.queryParameters["target"], "target", maxLength = maxStringLength)
            val benchmark = InputValidator.validateSymbol(call.request.queryParameters["benchmark"], "benchmark", maxLength = maxStringLength)
            val riskFreeRate = call.request.queryParameters["risk_free_rate"]?.toDoubleOrNull() ?: riskFreeRateDefault
            val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date", maxLength = maxStringLength) }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date", maxLength = maxStringLength) }
                ?: getToday()
            val alpha = alphaService.calculateAlpha(target, benchmark, fromDate, toDate, riskFreeRate)
            call.respond(HttpStatusCode.OK, alpha)
        }
    }
}
