package io.runtimerocket.plugin.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk

object RrNotifier {
    const val GROUP_ID = "RuntimeRocket"

    fun warnLimitedHotSwap(project: Project, sdk: Sdk?) {
        val name = sdk?.name ?: "this JDK"
        notify(
            project,
            "This JDK cannot add methods/fields ($name). Use JetBrains Runtime or continue with method-body only.",
            NotificationType.WARNING,
        )
    }

    fun handshakeTimedOut(project: Project) {
        notify(
            project,
            "RuntimeRocket did not attach within 90s. Forked launchers and Gradle bootRun are a common cause.",
            NotificationType.WARNING,
        )
    }

    fun handshakeFailed(project: Project, message: String?) {
        notify(
            project,
            "RuntimeRocket handshake failed${if (message.isNullOrBlank()) "" else ": $message"}",
            NotificationType.ERROR,
        )
    }

    fun attached(project: Project, backend: String) {
        notify(
            project,
            "RuntimeRocket attached ($backend HotSwap)",
            NotificationType.INFORMATION,
        )
    }

    fun hotSwapSetNeverPrompt(project: Project) {
        notify(
            project,
            "Set Settings → Debugger → HotSwap → Reload classes after compilation = Never so RuntimeRocket is the only reloader.",
            NotificationType.INFORMATION,
        )
    }

    fun reloadResult(project: Project, decision: RrReloadPresenter.Decision) {
        if (!decision.showBalloon) {
            return
        }
        val notification =
            NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(decision.balloonText, decision.balloonType)
        if (RrReloadPresenter.ACTION_RESTART in decision.actions) {
            notification.addAction(RestartRunConfigAction(project))
        }
        notification.notify(project)
    }

    fun notAttachedCompile(project: Project) {
        notify(project, "Compile finished — RuntimeRocket is not attached. Nothing reloaded.", NotificationType.WARNING)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, type)
            .notify(project)
    }
}
