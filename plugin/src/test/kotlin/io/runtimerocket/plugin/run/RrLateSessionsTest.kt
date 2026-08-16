package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant

class RrLateSessionsTest {
    @Test
    fun deadPidIsRemovedAndOnlyLiveSessionsRemain() {
        val livePids = mutableSetOf(20L)
        val late = RrLateSessions { it in livePids }
        val dead = session(10)
        val live = session(20)
        late.put(dead)
        late.put(live)

        assertTrue(late.isNotEmpty())
        assertEquals(listOf(20L), late.snapshot().map { it.pid })
        assertTrue(dead.closed)
        assertFalse(live.closed)

        val remaining = late.snapshot()
        assertEquals(1, remaining.size)
        assertEquals(20L, remaining.single().pid)

        livePids.clear()
        assertFalse(late.isNotEmpty())
        assertTrue(late.snapshot().isEmpty())
        assertTrue(live.closed)
    }

    @Test
    fun overwriteClosesPreviousSessionForSamePid() {
        val late = RrLateSessions { true }
        val first = session(7)
        val second = session(7)
        late.put(first)
        late.put(second)
        assertTrue(first.closed)
        assertFalse(second.closed)
        assertEquals(listOf(second), late.snapshot())
    }

    @Test
    fun sendReloadIteratesOnlyLiveSessions() {
        val late = RrLateSessions { it == 2L }
        late.put(session(1))
        late.put(session(2))
        val toSend = late.snapshot()
        assertEquals(listOf(2L), toSend.map { it.pid })
        assertFalse(toSend.any { it.pid == 1L })
    }

    @Test
    fun deadConnectionIsDetectedForLateAttachCleanup() {
        assertTrue(RrSessionManager.isDeadConnection(IOException("Connection refused")))
        assertTrue(RrSessionManager.isDeadConnection(IllegalStateException("socket closed")))
        assertFalse(RrSessionManager.isDeadConnection(IllegalStateException("not connected")))
    }

    @Test
    fun attachFailureKeepsStatusWhenAnotherSessionIsActive() {
        assertEquals("enhanced", AttachRuntimeRocketAction.failureKeepsAttached(true, "enhanced"))
        assertEquals("", AttachRuntimeRocketAction.failureKeepsAttached(true, null))
        assertEquals(null, AttachRuntimeRocketAction.failureKeepsAttached(false, "enhanced"))
    }

    private fun session(pid: Long): RrSession {
        return RrSession(
            token = "t$pid",
            handshake =
                HandshakeDocument(
                    pid = pid,
                    port = pid.toInt(),
                    token = "t$pid",
                    backend = "standard",
                    capabilities = emptyList(),
                    version = "0.1.0-SNAPSHOT",
                    startedAt = Instant.parse("2026-08-15T12:00:00Z"),
                ),
            processHandler = null,
        )
    }
}
