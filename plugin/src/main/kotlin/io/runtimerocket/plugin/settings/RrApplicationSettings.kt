package io.runtimerocket.plugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "RuntimeRocketApplication", storages = [Storage("runtimerocket.xml")])
class RrApplicationSettings : PersistentStateComponent<RrApplicationSettings.State> {

    data class State(
        var showSuccessBalloons: Boolean = false,
        var enableOnNewRunConfigurations: Boolean = true,
    )

    private var state = State()

    var showSuccessBalloons: Boolean
        get() = state.showSuccessBalloons
        set(value) {
            state.showSuccessBalloons = value
        }

    var enableOnNewRunConfigurations: Boolean
        get() = state.enableOnNewRunConfigurations
        set(value) {
            state.enableOnNewRunConfigurations = value
        }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): RrApplicationSettings {
            return ApplicationManager.getApplication().getService(RrApplicationSettings::class.java)
        }
    }
}
