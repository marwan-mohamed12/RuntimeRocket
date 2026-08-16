package io.runtimerocket.plugin.run

import io.runtimerocket.protocol.AdapterOutcome
import io.runtimerocket.protocol.ReloadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class LateAttachNotesTest {
    @Test
    fun springPartialNoteFromHandshakeIsDisplayed() {
        val handshake =
            HandshakeDocument(
                pid = 9,
                port = 1,
                token = "t",
                backend = "standard",
                capabilities = emptyList(),
                version = "0.1.0-SNAPSHOT",
                startedAt = Instant.parse("2026-08-15T12:00:00Z"),
                notes = listOf(LateAttachNotes.SPRING_INACTIVE_DETAIL),
            )
        val notes = LateAttachNotes.collect(handshake = handshake)
        val found = LateAttachNotes.springInactive(notes)
        assertEquals(LateAttachNotes.SPRING_INACTIVE_DETAIL, found)
        val decision = LateAttachNotes.displayDecision(handshake)
        assertNotNull(decision)
        assertEquals(ReloadResult.PARTIAL, decision!!.status)
        assertEquals(LateAttachNotes.SPRING_INACTIVE_DETAIL, decision.balloon)
        assertEquals(LateAttachNotes.SPRING_INACTIVE_DETAIL, decision.historyMessage)
        assertTrue(decision.toolWindowLine.startsWith("PARTIAL"))
        assertTrue(decision.toolWindowLine.contains(LateAttachNotes.SPRING_INACTIVE_MARKER))
        assertEquals(LateAttachNotes.toolWindowText(found!!), decision.toolWindowLine)
    }

    @Test
    fun springPartialNoteFromReloadResultIsDisplayed() {
        val result = ReloadResult()
        result.status = ReloadResult.PARTIAL
        result.message = LateAttachNotes.SPRING_INACTIVE_DETAIL
        val adapter = AdapterOutcome("spring", AdapterOutcome.PARTIAL, 1L, LateAttachNotes.SPRING_INACTIVE_DETAIL)
        result.adapters = listOf(adapter)

        val found = LateAttachNotes.springInactive(LateAttachNotes.collect(result = result))
        assertEquals(LateAttachNotes.SPRING_INACTIVE_DETAIL, found)
        assertEquals(LateAttachNotes.SPRING_INACTIVE_DETAIL, LateAttachNotes.fromAdapters(result.adapters))
    }

    @Test
    fun missingSpringNoteIsNullNotSilentSuccessString() {
        val handshake =
            HandshakeDocument(
                pid = 1,
                port = 2,
                token = "t",
                backend = "enhanced",
                capabilities = listOf("METHOD_BODY"),
                version = "0.1.0-SNAPSHOT",
                startedAt = Instant.parse("2026-08-15T12:00:00Z"),
            )
        assertNull(LateAttachNotes.springInactive(LateAttachNotes.collect(handshake = handshake)))
        assertNull(LateAttachNotes.fromAdapters(emptyList()))
    }

    @Test
    fun handshakeJsonNotesAreParsed() {
        val json =
            """{"pid":7,"port":9,"token":"x","backend":"standard","capabilities":[],"version":"0.1.0-SNAPSHOT","startedAt":"2026-08-15T12:00:00Z","notes":["Spring adapter inactive until a request hits the app or you restart with -javaagent (premain)."]}"""
        val doc = HandshakeDocument.parse(json)
        assertEquals(listOf(LateAttachNotes.SPRING_INACTIVE_DETAIL), doc.notes)
        assertNotNull(LateAttachNotes.springInactive(doc.notes))
        val decision = LateAttachNotes.displayDecision(doc)
        assertNotNull(decision)
        assertEquals(ReloadResult.PARTIAL, decision!!.status)
        assertEquals(doc.notes.single(), decision.historyMessage)
    }

    @Test
    fun displayDecisionIsNullWhenNoteMissing() {
        val handshake =
            HandshakeDocument(
                pid = 1,
                port = 2,
                token = "t",
                backend = "enhanced",
                capabilities = listOf("METHOD_BODY"),
                version = "0.1.0-SNAPSHOT",
                startedAt = Instant.parse("2026-08-15T12:00:00Z"),
            )
        assertNull(LateAttachNotes.displayDecision(handshake))
    }
}
