package com.rickd.mushclient.ui.worlds

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.World
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorldListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MudDatabase.get(application).worldDao()

    val worlds: StateFlow<List<World>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, host: String, port: Int) {
        viewModelScope.launch {
            if (id == null) {
                dao.insert(World(name = name, host = host, port = port))
            } else {
                dao.update(World(id = id, name = name, host = host, port = port))
            }
        }
    }

    fun delete(world: World) {
        viewModelScope.launch { dao.delete(world) }
    }
}
