package com.rickd.mushclient.ui.worlds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.MushCharacter
import com.rickd.mushclient.data.World
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CharacterListViewModel(
    application: Application,
    private val worldId: Long,
) : AndroidViewModel(application) {

    private val worldDao = MudDatabase.get(application).worldDao()
    private val characterDao = MudDatabase.get(application).characterDao()

    private val _world = MutableStateFlow<World?>(null)
    val world: StateFlow<World?> = _world.asStateFlow()

    val characters: StateFlow<List<MushCharacter>> = characterDao.observeForWorld(worldId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _world.value = worldDao.getById(worldId) }
    }

    fun save(id: Long?, name: String, password: String) {
        viewModelScope.launch {
            if (id == null) {
                characterDao.insert(MushCharacter(worldId = worldId, name = name, password = password))
            } else {
                characterDao.update(MushCharacter(id = id, worldId = worldId, name = name, password = password))
            }
        }
    }

    fun delete(character: MushCharacter) {
        viewModelScope.launch {
            characterDao.delete(character)
            worldDao.clearLastCharacterId(character.id)
        }
    }

    /** Records this world's most-recently-connected-as character, shown in the Address Book. */
    fun recordConnected(character: MushCharacter) {
        viewModelScope.launch { worldDao.setLastCharacterId(worldId, character.id) }
    }

    companion object {
        fun factory(application: Application, worldId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { CharacterListViewModel(application, worldId) }
            }
    }
}
