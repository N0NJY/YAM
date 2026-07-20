package com.rickd.mushclient.data

import androidx.room.Embedded

/** [worldName] null means [Trigger.worldId] is null — a Global trigger. */
data class TriggerWithWorldName(
    @Embedded val trigger: Trigger,
    val worldName: String?,
)
