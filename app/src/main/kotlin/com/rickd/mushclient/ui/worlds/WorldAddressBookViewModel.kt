package com.rickd.mushclient.ui.worlds

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rickd.mushclient.data.MudDatabase
import com.rickd.mushclient.data.World
import com.rickd.mushclient.data.WorldGroup
import com.rickd.mushclient.data.WorldWithLastCharacter
import com.rickd.mushclient.service.MudConnectionService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which set of worlds the Address Book's right pane is currently showing. */
sealed class GroupSelection {
    data object AllWorlds : GroupSelection()
    data object Ungrouped : GroupSelection()
    data object Deleted : GroupSelection()
    data class Named(val group: WorldGroup) : GroupSelection()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorldAddressBookViewModel(application: Application) : AndroidViewModel(application) {

    private val worldDao = MudDatabase.get(application).worldDao()
    private val groupDao = MudDatabase.get(application).worldGroupDao()

    // Lets the Address Book tell the user "picking a world won't lose your other session" —
    // but browsing Worlds must never be what *starts* MudConnectionService, so this binds
    // without BIND_AUTO_CREATE: if the service isn't already running, the bind silently no-ops
    // and activeTabCount just stays 0, exactly as if there were no live sessions.
    private val _activeTabCount = MutableStateFlow(0)
    val activeTabCount: StateFlow<Int> = _activeTabCount.asStateFlow()

    private var tabMirrorJob: Job? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as MudConnectionService.LocalBinder).getService()
            tabMirrorJob = viewModelScope.launch {
                service.uiState.collect { _activeTabCount.value = it.tabs.size }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            tabMirrorJob?.cancel()
            _activeTabCount.value = 0
        }
    }

    init {
        application.bindService(Intent(application, MudConnectionService::class.java), serviceConnection, 0)
    }

    override fun onCleared() {
        super.onCleared()
        // bindService above may never have actually attached (no-op when the service wasn't
        // running), in which case unbind throws — this screen doesn't know which happened.
        runCatching { getApplication<Application>().unbindService(serviceConnection) }
    }

    val groups: StateFlow<List<WorldGroup>> = groupDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<GroupSelection>(GroupSelection.AllWorlds)
    val selection: StateFlow<GroupSelection> = _selection.asStateFlow()

    // Filtering happens in SQL (one DAO query per selection), not client-side — matches this
    // codebase's existing convention (CharacterDao.observeForWorld) rather than fetching every
    // world and filtering in Kotlin.
    val worlds: StateFlow<List<WorldWithLastCharacter>> = _selection.flatMapLatest { selection ->
        when (selection) {
            GroupSelection.AllWorlds -> worldDao.observeAllWithLastCharacter()
            GroupSelection.Ungrouped -> worldDao.observeUngroupedWithLastCharacter()
            GroupSelection.Deleted -> worldDao.observeDeletedWithLastCharacter()
            is GroupSelection.Named -> worldDao.observeByGroupWithLastCharacter(selection.group.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A newly created world defaults into whatever named group is currently being viewed. */
    val currentGroupIdForNewWorld: Long?
        get() = (_selection.value as? GroupSelection.Named)?.group?.id

    fun selectGroup(selection: GroupSelection) {
        _selection.value = selection
    }

    fun save(id: Long?, name: String, host: String, port: Int, groupId: Long?) {
        viewModelScope.launch {
            if (id == null) {
                worldDao.insert(World(name = name, host = host, port = port, groupId = groupId))
            } else {
                // Preserve isDeleted/lastCharacterId — the editor only ever touches name/host/port/group.
                val existing = worldDao.getById(id) ?: return@launch
                worldDao.update(existing.copy(name = name, host = host, port = port, groupId = groupId))
            }
        }
    }

    /** Duplicates the World row only — not its characters (see plan decision 4). */
    fun copyWorld(world: World) {
        viewModelScope.launch {
            worldDao.insert(
                World(name = "${world.name} (Copy)", host = world.host, port = world.port, groupId = world.groupId),
            )
        }
    }

    fun softDelete(world: World) {
        viewModelScope.launch { worldDao.softDelete(world.id) }
    }

    fun restore(world: World) {
        viewModelScope.launch { worldDao.restore(world.id) }
    }

    fun permanentlyDelete(world: World) {
        viewModelScope.launch { worldDao.delete(world) }
    }

    fun newGroup(name: String) {
        viewModelScope.launch { groupDao.insert(WorldGroup(name = name)) }
    }

    /** Un-groups (never deletes) the group's worlds first — a group's worlds always survive it. */
    fun deleteGroup(group: WorldGroup) {
        if (_selection.value == GroupSelection.Named(group)) {
            _selection.value = GroupSelection.AllWorlds
        }
        viewModelScope.launch {
            worldDao.clearGroup(group.id)
            groupDao.delete(group)
        }
    }
}
