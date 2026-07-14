package com.rickd.mushclient.data

import androidx.room.Embedded

data class CharacterWithWorld(
    @Embedded val character: MushCharacter,
    val worldName: String,
    val worldHost: String,
    val worldPort: Int,
)
