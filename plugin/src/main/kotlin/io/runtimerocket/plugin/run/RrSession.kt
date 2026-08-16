package io.runtimerocket.plugin.run

import com.intellij.execution.process.ProcessHandler
import java.time.Instant

class RrSession(
    val token: String,
    val handshake: HandshakeDocument,
    val processHandler: ProcessHandler?,
    val attachedAt: Instant = Instant.now(),
) {
    val pid: Long get() = handshake.pid
    val port: Int get() = handshake.port
    val backend: String get() = handshake.backend
    val capabilities: List<String> get() = handshake.capabilities

    @Volatile
    var client: RrAgentClient? = null
        private set

    @Volatile
    var connected: Boolean = false
        private set

    fun connect(): RrAgentClient {
        val existing = client
        if (existing != null && connected) {
            return existing
        }
        val next = RrAgentClient(port = handshake.port, token = token)
        next.connect()
        client = next
        connected = true
        return next
    }

    fun close() {
        connected = false
        try {
            client?.close()
        } finally {
            client = null
        }
    }
}
