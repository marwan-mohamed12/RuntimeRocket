package io.runtimerocket.plugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Service(Service.Level.PROJECT)
class RrSessionManager(private val project: Project) {
    private val sessions = ConcurrentHashMap<ProcessHandler, RrSession>()
    private val executor: Executor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "rr-handshake").apply { isDaemon = true }
        }

    fun awaitHandshake(
        handler: ProcessHandler,
        expectedPid: Long?,
        expectedToken: String,
        startedAfter: Instant,
        timeout: Duration = HandshakeClient.TIMEOUT,
        client: HandshakeClient = HandshakeClient(),
    ): CompletableFuture<RrSession?> {
        return CompletableFuture.supplyAsync(
            {
                val handshake = client.await(expectedPid, expectedToken, startedAfter, timeout)
                if (handshake == null) {
                    return@supplyAsync null
                }
                val session = RrSession(expectedToken, handshake, handler)
                sessions[handler] = session
                RrHotSwapPolicy.onSessionAttached(project)
                session
            },
            executor,
        )
    }

    fun connect(session: RrSession): RrSession {
        session.connect()
        return session
    }

    fun disconnect(handler: ProcessHandler) {
        val session = sessions.remove(handler)
        session?.close()
        if (sessions.isEmpty()) {
            RrHotSwapPolicy.onSessionDetached(project)
        }
    }

    fun session(handler: ProcessHandler): RrSession? = sessions[handler]

    fun hasActiveSession(): Boolean = sessions.isNotEmpty()

    fun activeSessions(): Collection<RrSession> = sessions.values.toList()

    companion object {
        fun getInstance(project: Project): RrSessionManager {
            return project.getService(RrSessionManager::class.java)
        }
    }
}
