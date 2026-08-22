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
    internal val lateSessions = RrLateSessions()
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
            lateSessions.put(session)
        }
        RrHotSwapPolicy.onSessionAttached(project)
        return session
    }

    fun disconnect(handler: ProcessHandler) {
        val session = sessions.remove(handler)
        session?.let { lateSessions.disconnect(it.pid) }
        session?.close()
        if (!hasActiveSession()) {
            RrHotSwapPolicy.onSessionDetached(project)
        }
    }

    fun disconnectPid(pid: Long) {
        lateSessions.disconnect(pid)
        if (!hasActiveSession()) {
            RrHotSwapPolicy.onSessionDetached(project)
        }
    }

    fun disconnectSession(session: RrSession) {
        val handler = session.processHandler
        if (handler != null && sessions.containsKey(handler)) {
            disconnect(handler)
            return
        }
        disconnectPid(session.pid)
    }

    fun disconnectAll(): Int {
        val current = activeSessions().toList()
        for (session in current) {
            disconnectSession(session)
        }
        return current.size
    }

    fun session(handler: ProcessHandler): RrSession? = sessions[handler]

    fun sessionByPid(pid: Long): RrSession? = activeSessions().firstOrNull { it.pid == pid }

    fun hasActiveSession(): Boolean {
        lateSessions.pruneDead()
        return sessions.isNotEmpty() || lateSessions.isNotEmpty()
    }

    fun activeSessions(): Collection<RrSession> {
        lateSessions.pruneDead()
        return (sessions.values + lateSessions.snapshot()).distinct()
    }

    fun sendReload(request: ReloadRequest): List<ReloadResult> {
        lateSessions.pruneDead()
        val results = mutableListOf<ReloadResult>()
        for (session in activeSessions()) {
            try {
                results.add(session.sendReload(request))
            } catch (e: Exception) {
                if (!isDeadConnection(e)) {
                    throw e
                }
                val recovered = reconnect(session)
                if (recovered) {
                    results.add(session.sendReload(request))
                    continue
                }
                if (session.processHandler == null && !RrLateSessions.jvmAlive(session.pid)) {
                    lateSessions.disconnect(session.pid)
                    continue
                }
                val failed = ReloadResult()
                failed.status = ReloadResult.FAILED
                failed.message =
                    "agent communication failed for pid ${session.pid}; session kept because the process is still running"
                results.add(failed)
            }
        }
        return results
    }

    fun reconnect(session: RrSession): Boolean {
        return try {
            session.reconnect()
            true
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        internal fun isDeadConnection(error: Throwable): Boolean {
            val messages =
                generateSequence(error) { it.cause }
                    .mapNotNull { it.message }
                    .joinToString(" ")
                    .lowercase()
            return error is java.io.IOException ||
                error.cause is java.io.IOException ||
                messages.contains("connection refused") ||
                messages.contains("broken pipe") ||
                messages.contains("socket closed") ||
                messages.contains("connection reset")
        }

        fun getInstance(project: Project): RrSessionManager {
            return project.getService(RrSessionManager::class.java)
        }
    }
}
