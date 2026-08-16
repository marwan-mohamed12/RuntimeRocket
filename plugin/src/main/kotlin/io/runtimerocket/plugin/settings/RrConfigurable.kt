package io.runtimerocket.plugin.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class RrConfigurable(private val project: Project) : SearchableConfigurable {
    private var panel: JPanel? = null
    private val enabledBox = JBCheckBox("Enable RuntimeRocket")
    private val autoReloadBox = JBCheckBox("Reload after a successful compile")
    private val includeTestsBox = JBCheckBox("Include test output (JUnit)")
    private val preferEnhancedBox = JBCheckBox("Prefer enhanced HotSwap (JBR / DCEVM)")
    private val logLevelCombo = ComboBox(arrayOf("error", "warn", "info", "debug", "trace"))
    private val extraWatchField = JBTextField()

    override fun getId(): String = "io.runtimerocket"

    override fun getDisplayName(): String = "RuntimeRocket"

    override fun createComponent(): JComponent {
        val form =
            FormBuilder.createFormBuilder()
                .addComponent(enabledBox)
                .addComponent(autoReloadBox)
                .addComponent(includeTestsBox)
                .addComponent(preferEnhancedBox)
                .addLabeledComponent("Agent log level:", logLevelCombo)
                .addLabeledComponent("Extra watch dirs (path-separated):", extraWatchField)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        panel = form
        return form
    }

    override fun isModified(): Boolean {
        val settings = RrProjectSettings.getInstance(project)
        return enabledBox.isSelected != settings.enabled ||
            autoReloadBox.isSelected != settings.autoReloadOnSuccessfulCompile ||
            includeTestsBox.isSelected != settings.includeTests ||
            preferEnhancedBox.isSelected != settings.preferEnhanced ||
            (logLevelCombo.selectedItem as String) != settings.logLevel ||
            extraWatchField.text.trim() != settings.extraWatchDirs.joinToString(PATH_SEP)
    }

    override fun apply() {
        val settings = RrProjectSettings.getInstance(project)
        settings.enabled = enabledBox.isSelected
        settings.autoReloadOnSuccessfulCompile = autoReloadBox.isSelected
        settings.includeTests = includeTestsBox.isSelected
        settings.preferEnhanced = preferEnhancedBox.isSelected
        settings.logLevel = logLevelCombo.selectedItem as String
        settings.extraWatchDirs =
            extraWatchField.text
                .split(PATH_SEP)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()
    }

    override fun reset() {
        val settings = RrProjectSettings.getInstance(project)
        enabledBox.isSelected = settings.enabled
        autoReloadBox.isSelected = settings.autoReloadOnSuccessfulCompile
        includeTestsBox.isSelected = settings.includeTests
        preferEnhancedBox.isSelected = settings.preferEnhanced
        logLevelCombo.selectedItem = settings.logLevel
        extraWatchField.text = settings.extraWatchDirs.joinToString(PATH_SEP)
    }

    override fun disposeUIResources() {
        panel = null
    }

    companion object {
        private val PATH_SEP = System.getProperty("path.separator")
    }
}
