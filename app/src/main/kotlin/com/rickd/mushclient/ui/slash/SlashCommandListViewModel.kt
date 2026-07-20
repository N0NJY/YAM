package com.rickd.mushclient.ui.slash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.PatternMatchType
import com.rickd.mushclient.data.SlashCommand
import com.rickd.mushclient.data.SlashCommandWithWorldName
import com.rickd.mushclient.data.World
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SlashCommandListViewModel(application: Application) : AndroidViewModel(application) {

    private val worldDao = MudDatabase.get(application).worldDao()
    private val slashCommandDao = MudDatabase.get(application).slashCommandDao()

    val commands: StateFlow<List<SlashCommandWithWorldName>> = slashCommandDao.observeAllWithWorldName()
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
        sendTemplate: String,
    ) {
        val command = SlashCommand(
            id = id ?: 0,
            worldId = worldId,
            name = name,
            pattern = pattern,
            matchType = matchType,
            caseSensitive = caseSensitive,
            enabled = enabled,
            sendTemplate = sendTemplate,
        )
        viewModelScope.launch {
            if (id == null) slashCommandDao.insert(command) else slashCommandDao.update(command)
        }
    }

    fun setEnabled(command: SlashCommand, enabled: Boolean) {
        viewModelScope.launch { slashCommandDao.update(command.copy(enabled = enabled)) }
    }

    fun delete(command: SlashCommand) {
        viewModelScope.launch { slashCommandDao.delete(command) }
    }
}
