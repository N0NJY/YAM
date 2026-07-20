package com.rickd.mushclient.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

/**
 * The "⋮" entry point into Settings, reused on every screen so it's reachable no matter where
 * you are — e.g. checking a character's password or a world's host:port to share it, then
 * getting straight back to whatever you were doing (a live terminal included) via Back.
 */
@Composable
fun SettingsIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.MoreVert, contentDescription = "Settings")
    }
}
