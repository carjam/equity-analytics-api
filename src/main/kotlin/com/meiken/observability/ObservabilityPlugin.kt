package com.meiken.observability

import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.util.AttributeKey
import io.ktor.server.response.ApplicationSendPipeline
import org.slf4j.MDC
import kotlin.random.Random

private val StartTimeKey = AttributeKey<Long>("ObservabilityStartTime")
private const val CorrelationIdHeader = "X-Correlation-ID"
private const val MDC_CORRELATION_ID = "correlationId"

/**
 * Plugin that adds request observability: correlation ID (MDC + response header),
 * api_requests_total, api_request_duration_seconds, and in_flight_requests.
 * Requires [Metrics.init] to be called before this plugin is installed.
 */
val ObservabilityPlugin = io.ktor.server.application.createApplicationPlugin(name = "Observability") {
    onCall { call ->
        val startNanos = System.nanoTime()
        call.attributes.put(StartTimeKey, startNanos)

        val correlationId = call.request.headers[CorrelationIdHeader]
            ?: "req-${System.currentTimeMillis()}-${Random.nextInt(0, 99999)}"
        call.attributes.put(AttributeKey("CorrelationId"), correlationId)
        MDC.put(MDC_CORRELATION_ID, correlationId)
        call.response.headers.append(CorrelationIdHeader, correlationId)

        Metrics.incrementInFlight()

        call.response.pipeline.intercept(ApplicationSendPipeline.After) {
            val elapsedSeconds = (System.nanoTime() - startNanos) / 1e9
            val status = (call.response.status()?.value)?.toString() ?: "500"
            val method = call.request.httpMethod.value
            val endpoint = call.request.uri.toString().substringBefore('?')
            Metrics.recordApiRequest(endpoint, status, method, elapsedSeconds)
            Metrics.decrementInFlight()
            MDC.remove(MDC_CORRELATION_ID)
        }
    }
}
