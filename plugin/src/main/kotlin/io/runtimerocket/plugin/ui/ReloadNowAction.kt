package io.runtimerocket.plugin.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import io.runtimerocket.plugin.watch.RrReloadService

class ReloadNowAction : AnAction(
    "Reload Now",
    "Hot-reload first, then build and diagnose only if needed",
    RrIcons.Reload,
), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: e.getData(CommonDataKeys.PSI_FILE)?.virtualFile
        val module = e.getData(LangDataKeys.MODULE)
        RrReloadService.getInstance(project).reloadNow(file, module)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
