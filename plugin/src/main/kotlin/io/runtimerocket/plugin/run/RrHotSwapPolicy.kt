package io.runtimerocket.plugin.run

import com.intellij.debugger.ui.HotSwapUI
import com.intellij.debugger.ui.HotSwapVetoableListener
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskContext
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier
import java.util.concurrent.ConcurrentHashMap

/** Vetoes stock debugger HotSwap while an RR session is live. Does not write DebuggerSettings. */
object RrHotSwapPolicy {
    const val BRANCH_A = "A"
    const val BRANCH_B = "B"
    const val FOOTER_A = "session veto on"
    const val FOOTER_B = "set IDE HotSwap to Never"

    @Volatile
    var installedBranch: String = BRANCH_A
        private set

    private val vetoes = ConcurrentHashMap<Project, RrHotSwapVeto>()

    fun footerText(): String = if (installedBranch == BRANCH_A) FOOTER_A else FOOTER_B

    fun shouldAllowStockHotSwap(hasActiveRrSession: Boolean): Boolean = !hasActiveRrSession

    fun install(project: Project) {
        if (tryInstallVeto(project)) {
            installedBranch = BRANCH_A
        } else {
            installedBranch = BRANCH_B
        }
    }

    fun onSessionAttached(project: Project) {
        if (installedBranch == BRANCH_A) {
            vetoes[project]?.activate()
        } else {
            maybePromptPathB(project)
        }
    }

    fun onSessionDetached(project: Project) {
        if (installedBranch == BRANCH_A && !RrSessionManager.getInstance(project).hasActiveSession()) {
            vetoes[project]?.deactivate()
        }
    }

    private fun tryInstallVeto(project: Project): Boolean {
        return try {
            Class.forName("com.intellij.debugger.ui.HotSwapUI")
            Class.forName("com.intellij.debugger.ui.HotSwapVetoableListener")
            val veto = RrHotSwapVeto(project)
            vetoes[project] = veto
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun maybePromptPathB(project: Project) {
        val settings = RrProjectSettings.getInstance(project)
        if (settings.hotSwapPromptShown) {
            return
        }
        settings.hotSwapPromptShown = true
        RrNotifier.hotSwapSetNeverPrompt(project)
    }
}

internal class RrHotSwapVeto(private val project: Project) : HotSwapVetoableListener {
    @Volatile
    private var registered = false

    fun activate() {
        if (registered) {
            return
        }
        HotSwapUI.getInstance(project).addListener(this)
        registered = true
    }

    fun deactivate() {
        if (!registered) {
            return
        }
        HotSwapUI.getInstance(project).removeListener(this)
        registered = false
    }

    override fun shouldHotSwap(context: ProjectTaskContext): Boolean {
        return RrHotSwapPolicy.shouldAllowStockHotSwap(RrSessionManager.getInstance(project).hasActiveSession())
    }
}
