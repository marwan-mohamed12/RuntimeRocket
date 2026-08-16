package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class HandshakeDocumentTest {
    @Test
    fun parsesAgentHandshakeJson() {
        val json =
            """{"pid":4242,"port":53111,"token":"a3f1","backend":"enhanced","capabilities":["METHOD_BODY","ADD_METHOD"],"version":"0.1.0-SNAPSHOT","startedAt":"2026-08-15T12:00:00Z"}"""
        val doc = HandshakeDocument.parse(json)
        assertEquals(4242L, doc.pid)
        assertEquals(53111, doc.port)
        assertEquals("a3f1", doc.token)
        assertEquals("enhanced", doc.backend)
        assertEquals(listOf("METHOD_BODY", "ADD_METHOD"), doc.capabilities)
        assertEquals("0.1.0-SNAPSHOT", doc.version)
        assertEquals(Instant.parse("2026-08-15T12:00:00Z"), doc.startedAt)
    }
}
