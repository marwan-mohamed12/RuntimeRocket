package io.runtimerocket.plugin.run

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * Polls `${tmpdir}/runtimerocket/${pid}.json`, then scans `*.json` by token.
 * Timeout is 90 seconds (large Boot apps).
 */
class HandshakeClient(
    private val directory: Path = defaultDirectory(),
    private val clock: () -> Instant = { Instant.now() },
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it.toMillis()) },
) {
    fun await(
        expectedPid: Long?,
        expectedToken: String,
        startedAfter: Instant,
        timeout: Duration = TIMEOUT,
    ): HandshakeDocument? {
        val deadline = clock().plus(timeout)
        while (!clock().isAfter(deadline)) {
            val found = findOnce(expectedPid, expectedToken, startedAfter)
            if (found != null) {
                return found
            }
            val remaining = Duration.between(clock(), deadline)
            if (remaining.isZero || remaining.isNegative) {
                break
            }
            val slice = if (remaining < POLL) remaining else POLL
            sleeper(slice)
        }
        return findOnce(expectedPid, expectedToken, startedAfter)
    }

    fun findByPid(pid: Long): HandshakeDocument? {
        return readDocument(directory.resolve("$pid.json"))
    }

    fun findOnce(expectedPid: Long?, expectedToken: String, startedAfter: Instant): HandshakeDocument? {
        if (expectedPid != null) {
            val byPid = readIfMatches(directory.resolve("$expectedPid.json"), expectedToken, startedAfter)
            if (byPid != null) {
                return byPid
            }
        }
        if (!Files.isDirectory(directory)) {
            return null
        }
        Files.newDirectoryStream(directory, "*.json").use { stream ->
            for (file in stream) {
                val doc = readIfMatches(file, expectedToken, startedAfter)
                if (doc != null) {
                    return doc
                }
            }
        }
        return null
    }

    private fun readDocument(file: Path): HandshakeDocument? {
        if (!Files.isRegularFile(file)) {
            return null
        }
        val json =
            try {
                Files.readString(file, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                return null
            }
        return try {
            HandshakeDocument.parse(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun readIfMatches(file: Path, expectedToken: String, startedAfter: Instant): HandshakeDocument? {
        val doc = readDocument(file) ?: return null
        if (!tokenEquals(expectedToken, doc.token)) {
            return null
        }
        if (doc.startedAt.isBefore(startedAfter)) {
            return null
        }
        return doc
    }

    companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(90)
        val POLL: Duration = Duration.ofMillis(200)

        fun defaultDirectory(): Path = Path.of(System.getProperty("java.io.tmpdir"), "runtimerocket")

        fun tokenEquals(expected: String, actual: String): Boolean {
            val left = expected.toByteArray(StandardCharsets.UTF_8)
            val right = actual.toByteArray(StandardCharsets.UTF_8)
            return MessageDigest.isEqual(left, right)
        }
    }
}
