package io.runtimerocket.plugin.ui

import com.intellij.execution.RunManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import io.runtimerocket.plugin.run.RrRunConfigState
import io.runtimerocket.plugin.run.RrRunConfigSupport

class ToggleRrAction : ToggleAction(), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean {
        val profile = selected(e) ?: return false
        val defaultOn = RrRunConfigSupport.isApplicationFamily(profile)
        return RrRunConfigState.isEnabled(profile, defaultOn)
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val profile = selected(e) ?: return
        RrRunConfigState.setEnabled(profile, state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabledAndVisible = selected(e) != null
    }

    private fun selected(e: AnActionEvent) = e.project?.let { RunManager.getInstance(it).selectedConfiguration?.configuration }
}
