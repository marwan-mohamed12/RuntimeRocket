package io.runtimerocket.plugin.run

import java.util.concurrent.ConcurrentHashMap

/** Pid-keyed late-attach sessions. Dead JVMs are dropped; replace closes the previous session. */
internal class RrLateSessions(
    private val isProcessAlive: (Long) -> Boolean = Companion::jvmAlive,
) {
    private val byPid = ConcurrentHashMap<Long, RrSession>()

    fun put(session: RrSession) {
        val previous = byPid.put(session.pid, session)
        if (previous != null && previous !== session) {
            previous.close()
        }
    }

    fun disconnect(pid: Long): RrSession? {
        return byPid.remove(pid)?.also { it.close() }
    }

    fun pruneDead() {
        for (pid in byPid.keys.toList()) {
            if (!isProcessAlive(pid)) {
                disconnect(pid)
            }
        }
    }

    fun snapshot(): List<RrSession> {
        pruneDead()
        return byPid.values.toList()
    }

    fun isNotEmpty(): Boolean {
        pruneDead()
        return byPid.isNotEmpty()
    }

    companion object {
        fun jvmAlive(pid: Long): Boolean {
            return try {
                ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
            } catch (_: Exception) {
                false
            }
        }
    }
}
