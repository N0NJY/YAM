package com.rickd.mushclient.ui.triggers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.Trigger
import com.rickd.mushclient.data.PatternMatchType
import com.rickd.mushclient.data.TriggerWithWorldName
import com.rickd.mushclient.data.World
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TriggerListViewModel(application: Application) : AndroidViewModel(application) {

    private val worldDao = MudDatabase.get(application).worldDao()
    private val triggerDao = MudDatabase.get(application).triggerDao()

    val triggers: StateFlow<List<TriggerWithWorldName>> = triggerDao.observeAllWithWorldName()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val worlds: StateFlow<List<World>> = worldDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(
        id: Long?,
        worldId: Long?,
        name: String,
        pattern: String,
        matchType: PatternMatchType,
        caseSensitive: Boolean,
        enabled: Boolean,
        continueMatching: Boolean,
        omitFromDisplay: Boolean,
        recolorForeground: Int?,
        sendText: String?,
        inputText: String?,
    ) {
        val trigger = Trigger(
            id = id ?: 0,
            worldId = worldId,
            name = name,
            pattern = pattern,
            matchType = matchType,
            caseSensitive = caseSensitive,
            enabled = enabled,
            continueMatching = continueMatching,
            omitFromDisplay = omitFromDisplay,
            recolorForeground = recolorForeground,
            sendText = sendText,
            inputText = inputText,
        )
        viewModelScope.launch {
            if (id == null) triggerDao.insert(trigger) else triggerDao.update(trigger)
        }
    }

    fun setEnabled(trigger: Trigger, enabled: Boolean) {
        viewModelScope.launch { triggerDao.update(trigger.copy(enabled = enabled)) }
    }

    fun delete(trigger: Trigger) {
        viewModelScope.launch { triggerDao.delete(trigger) }
    }
}
