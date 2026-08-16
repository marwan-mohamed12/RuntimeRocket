package io.runtimerocket.plugin.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrStatus
import java.time.Instant

class RrExecutionListener : ExecutionListener {
    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val configuration = env.runProfile
        val project = env.project
        val settings = RrProjectSettings.getInstance(project)
        if (!settings.enabledFor(configuration)) {
            return
        }
        val sessionToken = TokenFactory.sessionToken(configuration) ?: return
        val pid = handler.rrPid()
        val startedAfter = sessionToken.createdAt.minusSeconds(2)
        RrStatus.waitingForAgent(project)
        val manager = RrSessionManager.getInstance(project)
        manager.awaitHandshake(
            handler = handler,
            expectedPid = pid,
            expectedToken = sessionToken.token,
            startedAfter = startedAfter,
        ).whenComplete { session, error ->
            if (error != null || session == null) {
                RrStatus.notAttached(project)
                RrNotifier.handshakeTimedOut(project)
                return@whenComplete
            }
            try {
                manager.connect(session)
                RrStatus.attached(project, session.backend)
                RrNotifier.attached(project, session.backend)
            } catch (e: Exception) {
                RrStatus.notAttached(project)
                RrNotifier.handshakeFailed(project, e.message)
            }
        }
    }

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int,
    ) {
        val project = env.project
        RrSessionManager.getInstance(project).disconnect(handler)
        val configuration = env.runProfile
        if (configuration is RunConfiguration) {
            TokenFactory.forget(configuration)
        }
        if (!RrSessionManager.getInstance(project).hasActiveSession()) {
            RrStatus.idle(project)
        }
    }
}
