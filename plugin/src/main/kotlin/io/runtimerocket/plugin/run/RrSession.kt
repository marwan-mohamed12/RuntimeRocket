package io.runtimerocket.plugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.application.ApplicationManager
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ReloadResult
import java.time.Instant

class RrSession(
    val token: String,
    val handshake: HandshakeDocument,
    val processHandler: ProcessHandler?,
    val environment: ExecutionEnvironment? = null,
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

    @Volatile
    var closed: Boolean = false
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
        closed = false
        return next
    }

    fun reconnect(): RrAgentClient {
        try {
            client?.close()
        } catch (_: Exception) {
        }
        client = null
        connected = false
        closed = false
        return connect()
    }

    fun sendReload(request: ReloadRequest): ReloadResult {
        val connectedClient = client ?: throw IllegalStateException("not connected")
        return connectedClient.sendReload(request)
    }

    fun restart() {
        val env = environment ?: return
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            ExecutionUtil.restart(env)
        } else {
            app.invokeLater { ExecutionUtil.restart(env) }
        }
    }

    fun close() {
        closed = true
        connected = false
        try {
            client?.close()
        } finally {
            client = null
        }
    }
}
