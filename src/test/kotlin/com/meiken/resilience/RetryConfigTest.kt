package com.meiken.resilience

import com.meiken.config.ResilienceConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RetryConfigTest {

    @Test
    fun `createRetry returns retry instance`() {
        val settings = ResilienceConfig.RetrySettings(
            maxAttempts = 3,
            waitDurationMs = 100L,
            multiplier = 2.0,
            maxWaitDurationMs = 1000L
        )
        val retry = RetryConfig.createRetry(settings)
        assertNotNull(retry)
    }

    @Test
    fun `retry executes and can succeed`() {
        val settings = ResilienceConfig.RetrySettings(3, 100L, 2.0, 1000L)
        val retry = RetryConfig.createRetry(settings)
        val result = retry.executeSupplier { "ok" }
        assertTrue(result == "ok")
    }
}
