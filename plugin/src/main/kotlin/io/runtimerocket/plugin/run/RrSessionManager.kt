package io.runtimerocket.plugin.run

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.runtimerocket.protocol.ReloadRequest
import io.runtimerocket.protocol.ReloadResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Service(Service.Level.PROJECT)
class RrSessionManager(private val project: Project) {
    private val sessions = ConcurrentHashMap<ProcessHandler, RrSession>()
    private val lateSessions = ConcurrentHashMap<Long, RrSession>()
    private val executor: Executor =
        Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "rr-handshake").apply { isDaemon = true }
        }

    fun awaitHandshake(
        handler: ProcessHandler,
        expectedPid: Long?,
        expectedToken: String,
        startedAfter: Instant,
        environment: ExecutionEnvironment? = null,
        timeout: Duration = HandshakeClient.TIMEOUT,
        client: HandshakeClient = HandshakeClient(),
    ): CompletableFuture<RrSession?> {
        return CompletableFuture.supplyAsync(
            {
                val handshake = client.await(expectedPid, expectedToken, startedAfter, timeout)
                if (handshake == null) {
                    return@supplyAsync null
                }
                RrSession(expectedToken, handshake, handler, environment)
            },
            executor,
        )
    }

    fun connect(session: RrSession): RrSession {
        try {
            session.connect()
        } catch (e: Exception) {
            session.close()
            throw e
        }
        val handler = session.processHandler
        if (handler != null) {
            sessions[handler] = session
        } else {
            lateSessions[session.pid] = session
        }
        RrHotSwapPolicy.onSessionAttached(project)
        return session
    }

    fun disconnect(handler: ProcessHandler) {
        val session = sessions.remove(handler)
        session?.let { lateSessions.remove(it.pid) }
        session?.close()
        if (!hasActiveSession()) {
            RrHotSwapPolicy.onSessionDetached(project)
        }
    }

    fun disconnectPid(pid: Long) {
        lateSessions.remove(pid)?.close()
        if (!hasActiveSession()) {
            RrHotSwapPolicy.onSessionDetached(project)
        }
    }

    fun session(handler: ProcessHandler): RrSession? = sessions[handler]

    fun hasActiveSession(): Boolean = sessions.isNotEmpty() || lateSessions.isNotEmpty()

    fun activeSessions(): Collection<RrSession> = (sessions.values + lateSessions.values).distinct()

    fun sendReload(request: ReloadRequest): List<ReloadResult> {
        return activeSessions().map { it.sendReload(request) }
    }

    companion object {
        fun getInstance(project: Project): RrSessionManager {
            return project.getService(RrSessionManager::class.java)
        }
    }
}
