package io.runtimerocket.plugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class RrStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = RrStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "RuntimeRocket"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = RrStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class RrStatusBarWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        project.messageBus.connect(this).subscribe(
            RrUiRefresh.TOPIC,
            RrUiRefresh { statusBar.updateWidget(WIDGET_ID) },
        )
    }

    override fun dispose() {}

    override fun getText(): String = RrStatus.widgetText(project)

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getTooltipText(): String {
        val snap = RrStatus.get(project)
        return when (snap.phase) {
            RrStatus.Phase.SUCCESS, RrStatus.Phase.PARTIAL ->
                "Reload finished in ${snap.latencyMs ?: 0} ms (compile time excluded)"
            RrStatus.Phase.RESTART_REQUIRED -> "Restart required — click to open RuntimeRocket"
            RrStatus.Phase.FAILED -> "Last reload failed — click to open RuntimeRocket"
            else -> "RuntimeRocket"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { RrStatus.openToolWindow(project) }
    }

    companion object {
        const val WIDGET_ID = "RuntimeRocket"
    }
}
