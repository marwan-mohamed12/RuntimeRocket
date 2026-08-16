package io.runtimerocket.plugin.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.JavaProgramPatcher
import io.runtimerocket.plugin.settings.RrProjectSettings
import io.runtimerocket.plugin.ui.RrNotifier

class RrJavaProgramPatcher : JavaProgramPatcher() {
    override fun patchJavaParameters(executor: Executor, configuration: RunProfile, params: JavaParameters) {
        val project = (configuration as? RunConfiguration)?.project ?: return
        val settings = RrProjectSettings.getInstance(project)
        if (!settings.enabledFor(configuration)) {
            return
        }

        val agentJar = AgentJarLocator.ensureUnpacked()
        val tokenFile = TokenFactory.writeSessionFile(configuration)
        val enhanced = JbrDetector.isEnhancedCapable(params.jdk)
        applyTo(params, agentJar, tokenFile, settings.logLevel, enhanced)

        if (!enhanced && settings.preferEnhanced && JbrDetector.jbrHome() != null) {
            RrNotifier.warnLimitedHotSwap(project, params.jdk)
        }
    }

    companion object {
        fun applyTo(
            params: JavaParameters,
            agentJar: java.nio.file.Path,
            tokenFile: java.nio.file.Path,
            logLevel: String,
            enhanced: Boolean,
        ) {
            for (arg in RrVmArguments.build(agentJar, tokenFile, logLevel, enhanced)) {
                params.vmParametersList.add(arg)
            }
        }
    }
}
