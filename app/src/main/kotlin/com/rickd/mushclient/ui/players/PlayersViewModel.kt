package com.rickd.mushclient.ui.players

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.CharacterWithWorld
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.MushCharacter
import com.rickd.mushclient.data.World
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayersViewModel(application: Application) : AndroidViewModel(application) {

    private val worldDao = MudDatabase.get(application).worldDao()
    private val characterDao = MudDatabase.get(application).characterDao()

    val players: StateFlow<List<CharacterWithWorld>> = characterDao.observeAllWithWorld()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val worlds: StateFlow<List<World>> = worldDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, worldId: Long, name: String, password: String) {
        viewModelScope.launch {
            if (id == null) {
                characterDao.insert(MushCharacter(worldId = worldId, name = name, password = password))
            } else {
                characterDao.update(MushCharacter(id = id, worldId = worldId, name = name, password = password))
            }
        }
    }

    fun delete(character: MushCharacter) {
        viewModelScope.launch { characterDao.delete(character) }
    }
}
