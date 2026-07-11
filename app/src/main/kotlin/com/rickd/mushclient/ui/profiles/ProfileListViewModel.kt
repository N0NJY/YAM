package com.rickd.mushclient.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.ConnectionProfile
import com.rickd.mushclient.data.MudDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MudDatabase.get(application).profileDao()

    val profiles: StateFlow<List<ConnectionProfile>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: Long?, name: String, host: String, port: Int) {
        viewModelScope.launch {
            if (id == null) {
                dao.insert(ConnectionProfile(name = name, host = host, port = port))
            } else {
                dao.update(ConnectionProfile(id = id, name = name, host = host, port = port))
            }
        }
    }

    fun delete(profile: ConnectionProfile) {
        viewModelScope.launch { dao.delete(profile) }
    }
}
