package com.meiken.api

import com.meiken.error.BadRequestException
import com.meiken.service.AlphaService
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

/**
 * GET /api/v1/alpha
 * Query: target, benchmark (required, 1-5 alphanumeric). from_date, to_date (optional; default YTD).
 * Returns 200 with JSON [Alpha] (annualized excess return) or 400/404/500 via StatusPages.
 */
fun Route.alphaRoutes(alphaService: AlphaService) {
    route("alpha") {
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
            val alpha = alphaService.calculateAlpha(target, benchmark, fromDate, toDate)
            call.respond(HttpStatusCode.OK, alpha)
        }
    }
}
