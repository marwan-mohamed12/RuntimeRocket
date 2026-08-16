package io.runtimerocket.plugin.run

import io.runtimerocket.protocol.Hello
import io.runtimerocket.protocol.HelloOk
import io.runtimerocket.protocol.Protocol
import io.runtimerocket.protocol.ProtocolCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

class RrAgentClientTest {
    @Test
    fun helloExchangeReturnsHelloOk() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val exec = Executors.newSingleThreadExecutor()
            val future =
                exec.submit<Hello> {
                    server.accept().use { socket ->
                        val line = socket.getInputStream().bufferedReader().readLine()
                        val hello = ProtocolCodec.decode(line) as Hello
                        val ok = HelloOk()
                        ok.session = hello.session
                        ok.seq = hello.seq
                        ok.backend = "enhanced"
                        ok.capabilities = listOf("METHOD_BODY")
                        ok.agentVersion = "0.1.0-SNAPSHOT"
                        ok.vmName = "test"
                        ok.javaVersion = "21"
                        val bytes = ProtocolCodec.encodeLine(ok).toByteArray()
                        socket.getOutputStream().write(bytes)
                        socket.getOutputStream().flush()
                        hello
                    }
                }
            RrAgentClient(port = port, token = "secret").use { client ->
                val ok = client.connect()
                assertEquals("enhanced", ok.backend)
                assertEquals("0.1.0-SNAPSHOT", ok.agentVersion)
                assertEquals(Protocol.VERSION, future.get().protocolVersion)
                assertEquals("secret", future.get().token)
            }
            exec.shutdownNow()
        }
    }

    @Test
    fun rejectsIncompatibleAgentVersion() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val exec = Executors.newSingleThreadExecutor()
            exec.submit {
                server.accept().use { socket ->
                    socket.getInputStream().bufferedReader().readLine()
                    val ok = HelloOk()
                    ok.backend = "standard"
                    ok.agentVersion = "9.9.0"
                    socket.getOutputStream().write(ProtocolCodec.encodeLine(ok).toByteArray())
                    socket.getOutputStream().flush()
                }
            }
            assertThrows(IllegalStateException::class.java) {
                RrAgentClient(port = port, token = "secret").use { it.connect() }
            }
            exec.shutdownNow()
        }
    }
}
