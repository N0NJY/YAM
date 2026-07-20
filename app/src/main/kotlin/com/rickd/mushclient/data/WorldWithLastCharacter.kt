package com.rickd.mushclient.data

import androidx.room.Embedded

/** A [World] plus the display name of whichever character was most recently connected as (if any). */
data class WorldWithLastCharacter(
    @Embedded val world: World,
    val lastCharacterName: String?,
)
