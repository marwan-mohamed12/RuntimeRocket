package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RrAttachStrategyTest {
    @Test
    fun prefersLiveSessionThenExistingHandshake() {
        assertEquals(RrAttachStrategy.Path.REUSE_SESSION, RrAttachStrategy.decide(true, true))
        assertEquals(RrAttachStrategy.Path.RECONNECT_HANDSHAKE, RrAttachStrategy.decide(false, true))
        assertEquals(RrAttachStrategy.Path.LOAD_AGENT, RrAttachStrategy.decide(false, false))
    }

    @Test
    fun timeoutFallsBackToExistingPidHandshake() {
        val existing =
            HandshakeDocument(
                pid = 7,
                port = 9,
                token = "old",
                backend = "standard",
                capabilities = emptyList(),
                version = "0.1.0-SNAPSHOT",
                startedAt = Instant.parse("2026-08-15T12:00:00Z"),
            )
        assertTrue(RrAttachStrategy.shouldReuseExistingHandshake(null, existing))
        assertFalse(RrAttachStrategy.shouldReuseExistingHandshake(existing, existing))
        assertFalse(RrAttachStrategy.shouldReuseExistingHandshake(null, null))
    }

    @Test
    fun alreadyStartedIsDetected() {
        assertTrue(RrAttachStrategy.alreadyStarted("agent already started; ignoring duplicate start"))
        assertFalse(RrAttachStrategy.alreadyStarted("Attach denied: wrong user"))
    }
}
