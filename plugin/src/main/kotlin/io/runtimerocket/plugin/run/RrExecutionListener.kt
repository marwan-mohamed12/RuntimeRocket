package io.runtimerocket.plugin.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier
import io.runtimerocket.plugin.ui.RrStatus
import io.runtimerocket.plugin.watch.RrReloadService

class RrExecutionListener : ExecutionListener {
    override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        val configuration = env.runProfile
        val project = env.project
        val settings = RrProjectSettings.getInstance(project)
        if (!settings.enabledFor(configuration)) {
            return
        }
        val sessionToken = TokenFactory.tokenFor(handler) ?: return
        val pid = handler.rrPid()
        val startedAfter = sessionToken.createdAt.minusSeconds(2)
        RrStatus.waitingForAgent(project)
        val manager = RrSessionManager.getInstance(project)
        manager.awaitHandshake(
            handler = handler,
            expectedPid = pid,
            expectedToken = sessionToken.token,
            startedAfter = startedAfter,
            environment = env,
        ).whenComplete { session, error ->
            if (error != null || session == null) {
                RrStatus.notAttached(project)
                RrNotifier.handshakeTimedOut(project)
                return@whenComplete
            }
            try {
                manager.connect(session)
                RrReloadService.getInstance(project).baseline()
                RrStatus.attached(project, session.backend)
                RrNotifier.attached(project, session.backend)
            } catch (e: Exception) {
                manager.disconnect(handler)
                TokenFactory.forget(handler)
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
        TokenFactory.forget(handler)
        if (!RrSessionManager.getInstance(project).hasActiveSession()) {
            RrStatus.idle(project)
        }
    }
}
