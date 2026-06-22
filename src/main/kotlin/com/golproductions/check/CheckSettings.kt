package com.golproductions.check

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "GolCheckSettings", storages = [Storage("gol-check.xml")])
class CheckSettings : PersistentStateComponent<CheckSettings.State> {

    data class State(var clientId: String = "", var enabled: Boolean = true)

    private var myState = State()

    var clientId: String
        get() = myState.clientId
        set(value) { myState.clientId = value }

    var enabled: Boolean
        get() = myState.enabled
        set(value) { myState.enabled = value }

    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    companion object {
        fun getInstance(): CheckSettings =
            ApplicationManager.getApplication().getService(CheckSettings::class.java)
    }
}
