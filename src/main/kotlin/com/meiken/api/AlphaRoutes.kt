package com.meiken.api

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
 * Alpha (excess return vs benchmark) from close-of-day returns. Query: target, benchmark (required). from_date, to_date (optional; default YTD).
 * Returns 200 with JSON [Alpha] or 400/404/500.
 */
fun Route.alphaRoutes(alphaService: AlphaService) {
    route("alpha") {
        get {
            val target = InputValidator.validateSymbol(call.request.queryParameters["target"], "target")
            val benchmark = InputValidator.validateSymbol(call.request.queryParameters["benchmark"], "benchmark")
            val fromDate = call.request.queryParameters["from_date"]?.let { InputValidator.validateDate(it, "from_date") }
                ?: getCurrentYearStart()
            val toDate = call.request.queryParameters["to_date"]?.let { InputValidator.validateDate(it, "to_date") }
                ?: getToday()
            val alpha = alphaService.calculateAlpha(target, benchmark, fromDate, toDate)
            call.respond(HttpStatusCode.OK, alpha)
        }
    }
}
