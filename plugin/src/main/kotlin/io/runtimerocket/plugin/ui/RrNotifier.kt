package io.runtimerocket.plugin.ui

import com.intellij.execution.configurations.RunProfile
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import io.runtimerocket.plugin.run.LateAttachNotes
import io.runtimerocket.plugin.run.RrJbrConsent
import io.runtimerocket.plugin.run.UseBundledJbrAction
import io.runtimerocket.plugin.settings.RrProjectSettings

object RrNotifier {
    const val GROUP_ID = "RuntimeRocket"

    fun warnLimitedHotSwap(project: Project, sdk: Sdk?, configuration: RunProfile? = null) {
        val settings = RrProjectSettings.getInstance(project)
        RrJbrConsent.markAsked(settings)
        val name = sdk?.name ?: "this JDK"
        val notification =
            NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(
                    "This JDK cannot add methods/fields ($name). Use JetBrains Runtime or continue with method-body only.",
                    NotificationType.WARNING,
                )
        notification.addAction(UseBundledJbrAction.notificationAction(project, configuration))
        notification.addAction(UseBundledJbrAction.declineNotificationAction(project))
        notification.notify(project)
    }

    fun handshakeTimedOut(project: Project) {
        val hybris = io.runtimerocket.plugin.run.RrHybrisProject.isHybris(project)
        val text =
            if (hybris) {
                "RuntimeRocket did not attach within 90s. For Hybris: wait until HAC is up, then Tools | Attach RuntimeRocket. If the handshake folder is under hybris/temp, link it to %TEMP%\\runtimerocket (see the Hybris guide)."
            } else {
                "RuntimeRocket did not attach within 90s. Forked launchers and Gradle bootRun are a common cause. Use Attach if the process was not started from an Application run configuration."
            }
        notify(project, text, NotificationType.WARNING)
    }

    fun missingLaunchToken(project: Project) {
        notify(
            project,
            "RuntimeRocket is enabled on this run configuration but the launch token was not found on the process. Use Tools | Attach RuntimeRocket.",
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

    fun detached(project: Project, message: String) {
        notify(project, message, NotificationType.INFORMATION)
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

    fun attachFailed(project: Project, message: String) {
        notify(project, message, NotificationType.ERROR)
    }

    fun lateAttachSpringInactive(project: Project, note: String) {
        notify(project, LateAttachNotes.balloonText(note), NotificationType.WARNING)
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(content, type)
            .notify(project)
    }
}
