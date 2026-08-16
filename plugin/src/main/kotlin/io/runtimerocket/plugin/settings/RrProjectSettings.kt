package io.runtimerocket.plugin.settings

import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import io.runtimerocket.plugin.run.RrRunConfigState
import io.runtimerocket.plugin.run.RrRunConfigSupport

@Service(Service.Level.PROJECT)
@State(name = "RuntimeRocket", storages = [Storage("runtimerocket.xml")])
class RrProjectSettings : PersistentStateComponent<RrProjectSettings.State> {

    data class State(
        var enabled: Boolean = true,
        var autoReloadOnSuccessfulCompile: Boolean = true,
        var includeTests: Boolean = false,
        var preferEnhanced: Boolean = true,
        var extraWatchDirs: MutableList<String> = mutableListOf(),
        var disabledAdapters: MutableList<String> = mutableListOf(),
        var logLevel: String = "info",
        var hotSwapPromptShown: Boolean = false,
    )

    private var state = State()

    var enabled: Boolean
        get() = state.enabled
        set(value) {
            state.enabled = value
        }

    var autoReloadOnSuccessfulCompile: Boolean
        get() = state.autoReloadOnSuccessfulCompile
        set(value) {
            state.autoReloadOnSuccessfulCompile = value
        }

    var includeTests: Boolean
        get() = state.includeTests
        set(value) {
            state.includeTests = value
        }

    var preferEnhanced: Boolean
        get() = state.preferEnhanced
        set(value) {
            state.preferEnhanced = value
        }

    var extraWatchDirs: MutableList<String>
        get() = state.extraWatchDirs
        set(value) {
            state.extraWatchDirs = value
        }

    var disabledAdapters: MutableList<String>
        get() = state.disabledAdapters
        set(value) {
            state.disabledAdapters = value
        }

    var logLevel: String
        get() = state.logLevel
        set(value) {
            state.logLevel = value
        }

    var hotSwapPromptShown: Boolean
        get() = state.hotSwapPromptShown
        set(value) {
            state.hotSwapPromptShown = value
        }

    fun enabledFor(configuration: RunProfile): Boolean {
        if (!state.enabled) {
            return false
        }
        if (!RrRunConfigSupport.isPatchable(configuration, state.includeTests)) {
            return false
        }
        val defaultOn = RrRunConfigSupport.isApplicationFamily(configuration)
        return RrRunConfigState.isEnabled(configuration, defaultOn)
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): RrProjectSettings {
            return project.getService(RrProjectSettings::class.java)
        }
    }
}
