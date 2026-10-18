package com.rickd.mushclient.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rickd.mushclient.service.MudConnectionService

/**
 * The "⋮" menu, reused on every screen so Settings and Exit are reachable no matter where you
 * are — e.g. checking a character's password or a world's host:port to share it, then getting
 * straight back to whatever you were doing (a live terminal included) via Back.
 */
@Composable
fun AppMenuButton(onOpenSettings: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val live by MudConnectionService.liveConnections.collectAsState()
    val context = LocalContext.current

    // The Box is what anchors the popup to the button and keeps the pair a single child of
    // whatever header Row this sits in.
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Settings") },
                onClick = {
                    menuOpen = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text("Exit") },
                onClick = {
                    menuOpen = false
                    if (exitConfirmMessage(live) == null) exitApp(context) else confirmExit = true
                },
            )
        }
    }
    if (confirmExit) {
        ConfirmDialog(
            title = "Exit YAM?",
            message = exitConfirmMessage(live) ?: "",
            confirmLabel = "Exit",
            onConfirm = {
                confirmExit = false
                exitApp(context)
            },
            onDismiss = { confirmExit = false },
        )
    }
}

/** What to ask before exiting, or null when nothing is connected and no question is needed. */
fun exitConfirmMessage(liveConnections: Int): String? = when {
    liveConnections <= 0 -> null
    liveConnections == 1 -> "1 connection is open and will be closed."
    else -> "$liveConnections connections are open and will be closed."
}

/** Closes every connection, stops the background service, and removes the app from Recents. */
private fun exitApp(context: Context) {
    context.startService(
        Intent(context, MudConnectionService::class.java).setAction(MudConnectionService.ACTION_EXIT),
    )
    var c: Context? = context
    while (c is ContextWrapper) {
        if (c is Activity) {
            c.finishAndRemoveTask()
            return
        }
        c = c.baseContext
    }
}
