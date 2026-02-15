package com.meiken.lifecycle

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

class ShutdownStateTest {

    @Test
    fun `isShuttingDown returns false before signalShutdown`() {
        assertFalse(ShutdownState.isShuttingDown())
    }
}
