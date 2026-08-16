package io.runtimerocket.plugin.run

import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.util.Key

object RrRunConfigState {
    const val ELEMENT = "runtimerocket"
    const val ATTR_ENABLED = "enabled"

    val ENABLED_KEY: Key<Boolean> = Key.create("io.runtimerocket.run.enabled")

    fun isEnabled(profile: RunProfile, defaultOn: Boolean): Boolean {
        val base = profile as? RunConfigurationBase<*> ?: return defaultOn
        return base.getUserData(ENABLED_KEY) ?: defaultOn
    }

    fun setEnabled(profile: RunProfile, enabled: Boolean) {
        val base = profile as? RunConfigurationBase<*> ?: return
        base.putUserData(ENABLED_KEY, enabled)
    }
}
