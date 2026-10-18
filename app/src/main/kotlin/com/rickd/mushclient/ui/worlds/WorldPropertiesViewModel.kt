package com.rickd.mushclient.ui.worlds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.World
import com.rickd.mushclient.data.WorldGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorldPropertiesViewModel(application: Application) : AndroidViewModel(application) {
    private val worldDao = MudDatabase.get(application).worldDao()
    private val groupDao = MudDatabase.get(application).worldGroupDao()

    val groups: StateFlow<List<WorldGroup>> = groupDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _world = MutableStateFlow<World?>(null)
    val world: StateFlow<World?> = _world.asStateFlow()

    // Set once the lookup finishes, so "still loading" and "no such world" can differ.
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun load(worldId: Long) {
        if (_loaded.value) return
        viewModelScope.launch {
            _world.value = worldDao.getById(worldId)
            _loaded.value = true
        }
    }

    fun save(updated: World, onSaved: () -> Unit) {
        viewModelScope.launch {
            worldDao.update(updated)
            onSaved()
        }
    }
}
