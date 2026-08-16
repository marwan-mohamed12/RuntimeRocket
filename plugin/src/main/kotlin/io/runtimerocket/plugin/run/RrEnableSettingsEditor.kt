package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.options.SettingsEditor
import com.intellij.ui.components.JBCheckBox
import javax.swing.JComponent

class RrEnableSettingsEditor<P : RunConfigurationBase<*>>(
    private val defaultOn: Boolean,
) : SettingsEditor<P>() {
    private val checkbox = JBCheckBox("Enable RuntimeRocket")

    override fun resetEditorFrom(s: P) {
        checkbox.isSelected = RrRunConfigState.isEnabled(s, defaultOn)
    }

    override fun applyEditorTo(s: P) {
        RrRunConfigState.setEnabled(s, checkbox.isSelected)
    }

    override fun createEditor(): JComponent = checkbox
}
