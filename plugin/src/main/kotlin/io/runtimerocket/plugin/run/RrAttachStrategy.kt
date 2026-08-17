package io.runtimerocket.plugin.run

/** Decide whether Attach should reuse a live agent instead of loading it again. */
object RrAttachStrategy {
    enum class Path {
        REUSE_SESSION,
        RECONNECT_HANDSHAKE,
        LOAD_AGENT,
    }

    fun decide(hasSessionForPid: Boolean, handshakeForPid: Boolean): Path {
        return when {
            hasSessionForPid -> Path.REUSE_SESSION
            handshakeForPid -> Path.RECONNECT_HANDSHAKE
            else -> Path.LOAD_AGENT
        }
    }

    fun shouldReuseExistingHandshake(
        newHandshake: HandshakeDocument?,
        existingByPid: HandshakeDocument?,
    ): Boolean {
        return newHandshake == null && existingByPid != null
    }

    fun alreadyStarted(error: String?): Boolean {
        val lower = error?.lowercase().orEmpty()
        return lower.contains("already started") ||
            lower.contains("agent already") ||
            lower.contains("duplicate start")
    }
}
