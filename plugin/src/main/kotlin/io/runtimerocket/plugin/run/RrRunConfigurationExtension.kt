package io.runtimerocket.plugin.run

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.options.SettingsEditor
import io.runtimerocket.plugin.settings.RrApplicationSettings
import org.jdom.Element

/** Checkbox on Application and Jar Application run configurations. */
class RrRunConfigurationExtension : RunConfigurationExtension() {
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        val id = configuration.type.id
        return id == RrRunConfigSupport.APPLICATION_TYPE_ID ||
            id == RrRunConfigSupport.JAR_APPLICATION_TYPE_ID
    }

    override fun isEnabledFor(applicableConfiguration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean {
        return true
    }

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        configuration: T,
        params: JavaParameters,
        runnerSettings: RunnerSettings?,
    ) {
        // VM args are injected by RrJavaProgramPatcher so Run and Debug share one path.
    }

    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) {
        val child = element.getChild(RrRunConfigState.ELEMENT) ?: return
        val raw = child.getAttributeValue(RrRunConfigState.ATTR_ENABLED) ?: return
        RrRunConfigState.setEnabled(runConfiguration, raw.toBoolean())
    }

    override fun writeExternal(runConfiguration: RunConfigurationBase<*>, element: Element) {
        val enabled = runConfiguration.getUserData(RrRunConfigState.ENABLED_KEY) ?: return
        val child = Element(RrRunConfigState.ELEMENT)
        child.setAttribute(RrRunConfigState.ATTR_ENABLED, enabled.toString())
        element.addContent(child)
    }

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P> {
        return RrEnableSettingsEditor(defaultOn = true)
    }

    override fun getEditorTitle(): String = "RuntimeRocket"

    override fun getSerializationId(): String = "runtimerocket"

    override fun attachToProcess(
        configuration: RunConfigurationBase<*>,
        handler: ProcessHandler,
        runnerSettings: RunnerSettings?,
    ) {
        TokenFactory.bindFromProcess(handler)
    }

    override fun extendCreatedConfiguration(configuration: RunConfigurationBase<*>, location: com.intellij.execution.Location<*>) {
        val enable = RrApplicationSettings.getInstance().enableOnNewRunConfigurations
        RrRunConfigState.setEnabled(configuration, enable)
    }
}
