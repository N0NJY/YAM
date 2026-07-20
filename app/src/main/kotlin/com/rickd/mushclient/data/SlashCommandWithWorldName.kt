package com.rickd.mushclient.data

import androidx.room.Embedded

/** [worldName] null means [SlashCommand.worldId] is null — a Global command. */
data class SlashCommandWithWorldName(
    @Embedded val command: SlashCommand,
    val worldName: String?,
)
