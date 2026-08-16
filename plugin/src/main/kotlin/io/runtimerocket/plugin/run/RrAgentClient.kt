package io.runtimerocket.plugin.run

import io.runtimerocket.plugin.PluginVersion
import io.runtimerocket.protocol.Frame
import io.runtimerocket.protocol.Hello
import io.runtimerocket.protocol.HelloOk
import io.runtimerocket.protocol.Protocol
import io.runtimerocket.protocol.ProtocolCodec
import io.runtimerocket.protocol.ProtocolException
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ReloadResult
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID

/** JSON-lines client for the agent's loopback server. First message must be Hello. */
class RrAgentClient(
    private val port: Int,
    private val token: String,
    private val host: String = "127.0.0.1",
    private val helloReadTimeoutMs: Int = HELLO_READ_TIMEOUT_MS,
) : AutoCloseable {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    var sessionId: String = UUID.randomUUID().toString()
        private set
    var helloOk: HelloOk? = null
        private set
    private var seq: Long = 0

    fun connect(): HelloOk {
        val sock = Socket()
        sock.tcpNoDelay = true
        sock.connect(InetSocketAddress(InetAddress.getByName(host), port), CONNECT_TIMEOUT_MS)
        sock.soTimeout = helloReadTimeoutMs
        socket = sock
        reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8))
        val hello = Hello()
        hello.token = token
        hello.pluginVersion = PluginVersion.current()
        hello.protocolVersion = Protocol.VERSION
        hello.session = sessionId
        hello.seq = nextSeq()
        write(hello)
        val line = reader?.readLine() ?: throw IllegalStateException("agent closed during hello")
        val frame = ProtocolCodec.decode(line)
        val ok = frame as? HelloOk ?: throw IllegalStateException("expected hello-ok, got ${frame.type}")
        if (!PluginVersion.compatibleWithAgent(ok.agentVersion)) {
            close()
            throw IllegalStateException(
                "agent version ${ok.agentVersion} is incompatible with plugin ${PluginVersion.current()}",
            )
        }
        helloOk = ok
        sock.soTimeout = 0
        return ok
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val HELLO_READ_TIMEOUT_MS = 5_000
        const val RELOAD_READ_TIMEOUT_MS = 30_000
    }

    fun send(frame: Frame) {
        if (frame.session == null) {
            frame.session = sessionId
        }
        if (frame.seq == 0L) {
            frame.seq = nextSeq()
        }
        write(frame)
    }

    @Synchronized
    fun sendReload(request: ReloadRequest, timeoutMs: Int = RELOAD_READ_TIMEOUT_MS): ReloadResult {
        val sock = socket ?: throw IllegalStateException("not connected")
        val previous = sock.soTimeout
        sock.soTimeout = timeoutMs
        try {
            send(request)
            while (true) {
                val frame = readFrame() ?: throw IllegalStateException("agent closed during reload")
                if (frame is ReloadResult) {
                    return frame
                }
            }
        } finally {
            sock.soTimeout = previous
        }
    }

    fun readFrame(): Frame? {
        val line = reader?.readLine() ?: return null
        if (line.isEmpty()) {
            return readFrame()
        }
        return try {
            ProtocolCodec.decode(line)
        } catch (e: ProtocolException) {
            throw IllegalStateException("invalid frame from agent", e)
        }
    }

    private fun write(frame: Frame) {
        val out = writer ?: throw IllegalStateException("not connected")
        out.write(ProtocolCodec.encodeLine(frame))
        out.flush()
    }

    @Synchronized
    private fun nextSeq(): Long {
        seq += 1
        return seq
    }

    override fun close() {
        try {
            writer?.close()
        } catch (_: Exception) {
            // closing
        }
        try {
            reader?.close()
        } catch (_: Exception) {
            // closing
        }
        try {
            socket?.close()
        } catch (_: Exception) {
            // closing
        }
        writer = null
        reader = null
        socket = null
    }
}
