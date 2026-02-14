package com.meiken.lifecycle

import com.meiken.observability.Metrics
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks whether the application is shutting down.
 * Set by shutdown hook; health endpoint returns 503 when true.
 */
object ShutdownState {
    private val shuttingDown = AtomicBoolean(false)
    private val log = LoggerFactory.getLogger(ShutdownState::class.java)

    fun isShuttingDown(): Boolean = shuttingDown.get()

    fun signalShutdown() {
        if (shuttingDown.compareAndSet(false, true)) {
            val start = System.nanoTime()
            log.info("Shutdown signal received; stopping new requests. Waiting up to 30s for in-flight requests.")
            try {
                Thread.sleep(30_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val durationSeconds = (System.nanoTime() - start) / 1e9
            Metrics.recordGracefulShutdownDuration(durationSeconds)
            log.info("Graceful shutdown wait completed ({}s)", durationSeconds)
        }
    }
}
