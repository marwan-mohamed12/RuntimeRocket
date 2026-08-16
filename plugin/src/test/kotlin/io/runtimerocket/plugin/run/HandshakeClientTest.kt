package io.runtimerocket.plugin.run

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

class HandshakeClientTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun findsHandshakeByPidWhenTokenMatches() {
        val token = "abc123"
        val started = Instant.parse("2026-08-15T12:00:00Z")
        writeHandshake(temp.resolve("4242.json"), pid = 4242, port = 53111, token = token, startedAt = started)

        val client = HandshakeClient(directory = temp, sleeper = { })
        val found = client.findOnce(expectedPid = 4242L, expectedToken = token, startedAfter = started.minusSeconds(1))
        assertEquals(4242L, found?.pid)
        assertEquals(53111, found?.port)
        assertEquals(token, found?.token)
    }

    @Test
    fun scansByTokenWhenExpectedPidIsNull() {
        val token = "parent-launcher"
        val started = Instant.parse("2026-08-15T12:00:00Z")
        writeHandshake(temp.resolve("999.json"), pid = 999, port = 53113, token = token, startedAt = started)

        val client = HandshakeClient(directory = temp, sleeper = { })
        val found = client.findOnce(expectedPid = null, expectedToken = token, startedAfter = started.minusSeconds(1))
        assertEquals(999L, found?.pid)
        assertEquals(53113, found?.port)
    }

    @Test
    fun ignoresPidFileWithWrongTokenAndScans() {
        val token = "expected-token"
        val started = Instant.parse("2026-08-15T12:00:00Z")
        writeHandshake(temp.resolve("100.json"), pid = 100, port = 1, token = "other", startedAt = started)
        writeHandshake(temp.resolve("999.json"), pid = 999, port = 53112, token = token, startedAt = started)

        val client = HandshakeClient(directory = temp, sleeper = { })
        val found = client.findOnce(expectedPid = 100L, expectedToken = token, startedAfter = started.minusSeconds(1))
        assertEquals(999L, found?.pid)
        assertEquals(53112, found?.port)
    }

    @Test
    fun ignoresStaleStartedAt() {
        val token = "tok"
        val started = Instant.parse("2026-08-15T12:00:00Z")
        writeHandshake(temp.resolve("7.json"), pid = 7, port = 9, token = token, startedAt = started.minusSeconds(30))

        val client = HandshakeClient(directory = temp, sleeper = { })
        val found = client.findOnce(expectedPid = 7L, expectedToken = token, startedAfter = started)
        assertNull(found)
    }

    @Test
    fun awaitTimesOutWhenMissing() {
        var now = Instant.parse("2026-08-15T12:00:00Z")
        val client =
            HandshakeClient(
                directory = temp,
                clock = { now },
                sleeper = { d -> now = now.plus(d) },
            )
        val found =
            client.await(
                expectedPid = 1L,
                expectedToken = "missing",
                startedAfter = now.minusSeconds(1),
                timeout = Duration.ofMillis(50),
            )
        assertNull(found)
    }

    @Test
    fun awaitSeesFileWrittenAfterFirstPoll() {
        val token = "late"
        val started = Instant.parse("2026-08-15T12:00:00Z")
        var now = started
        var polls = 0
        val client =
            HandshakeClient(
                directory = temp,
                clock = { now },
                sleeper = { d ->
                    polls++
                    if (polls == 1) {
                        writeHandshake(temp.resolve("8.json"), pid = 8, port = 42, token = token, startedAt = started)
                    }
                    now = now.plus(d)
                },
            )
        val found =
            client.await(
                expectedPid = 8L,
                expectedToken = token,
                startedAfter = started.minusSeconds(1),
                timeout = Duration.ofSeconds(1),
            )
        assertEquals(42, found?.port)
    }

    @Test
    fun handshakeTimeoutIsNinetySeconds() {
        assertEquals(90, HandshakeClient.TIMEOUT.seconds)
    }

    private fun writeHandshake(file: Path, pid: Long, port: Int, token: String, startedAt: Instant) {
        Files.writeString(
            file,
            """{"pid":$pid,"port":$port,"token":"$token","backend":"enhanced","capabilities":["METHOD_BODY"],"version":"0.1.0-SNAPSHOT","startedAt":"$startedAt"}""",
        )
    }
}
