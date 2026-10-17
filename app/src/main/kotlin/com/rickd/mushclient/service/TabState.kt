package com.rickd.mushclient.service

import com.rickd.mushclient.ui.session.TabMeta

// Pure reducers over the tab list, kept out of MudConnectionService so they can be unit-tested
// without Android. Tracked by connection id, never by list position — positions shift as tabs
// open and close.

/**
 * Marks [id] as having unseen activity, unless it is the tab currently in view. Only real
 * server text should call this; local echoes and notices are not "activity".
 */
fun List<TabMeta>.withActivity(id: Long, activeId: Long?): List<TabMeta> =
    if (id == activeId) this else map { if (it.id == id && !it.hasUnseenActivity) it.copy(hasUnseenActivity = true) else it }

/** Switching to a tab clears its activity flag. */
fun List<TabMeta>.withActivityCleared(id: Long): List<TabMeta> =
    map { if (it.id == id && it.hasUnseenActivity) it.copy(hasUnseenActivity = false) else it }

/** Records which Character the tab logged in as, for the second line of its label. */
fun List<TabMeta>.withCharacter(id: Long, characterName: String): List<TabMeta> =
    map { if (it.id == id) it.copy(characterName = characterName) else it }
