package com.rickd.mushclient.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rickd.mushclient.ui.help.HelpScreen
import com.rickd.mushclient.ui.players.PlayersScreen
import com.rickd.mushclient.ui.session.SessionScreen
import com.rickd.mushclient.ui.settings.SettingsScreen
import com.rickd.mushclient.ui.slash.SlashCommandListScreen
import com.rickd.mushclient.ui.triggers.TriggerListScreen
import com.rickd.mushclient.ui.worlds.CharacterListScreen
import com.rickd.mushclient.ui.worlds.WorldAddressBookScreen

private const val ROUTE_WORLDS = "worlds"
private const val ROUTE_CHARACTERS = "characters/{worldId}"
private const val ROUTE_SESSION =
    "session?host={host}&port={port}&charName={charName}&charPass={charPass}&worldId={worldId}&worldName={worldName}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PLAYERS = "players"
private const val ROUTE_TRIGGERS = "triggers"
private const val ROUTE_SLASH_COMMANDS = "slashcommands"
private const val ROUTE_HELP = "help"

private fun NavController.navigateToSession(
    host: String,
    port: Int,
    characterName: String?,
    characterPassword: String?,
    worldId: Long?,
    worldName: String?,
) {
    val charArgs = if (characterName != null && characterPassword != null) {
        "&charName=${Uri.encode(characterName)}&charPass=${Uri.encode(characterPassword)}"
    } else {
        ""
    }
    val worldArg = if (worldId != null) "&worldId=$worldId" else ""
    val worldNameArg = if (worldName != null) "&worldName=${Uri.encode(worldName)}" else ""
    navigate("session?host=${Uri.encode(host)}&port=$port$charArgs$worldArg$worldNameArg") {
        // Opening another tab reuses this back-stack entry instead of pushing a new one, so
        // repeated tab-opens don't accumulate Session entries — but a *first* entry into
        // Session (nothing to pop) leaves the Worlds→Characters/Players history intact, so
        // Back still returns to wherever the user launched Session from.
        popUpTo(ROUTE_SESSION) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun MudNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_WORLDS) {
        composable(ROUTE_WORLDS) {
            WorldAddressBookScreen(
                onSelectWorld = { world -> navController.navigate("characters/${world.id}") },
                onConnectManually = { navController.navigate("session") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                // Null on the app's true start destination — non-null when reached via a push
                // (e.g. from Session's "Worlds" button), so there's something to pop back to.
                onBack = navController.previousBackStackEntry?.let { { navController.popBackStack() } },
            )
        }
        composable(
            route = ROUTE_CHARACTERS,
            arguments = listOf(navArgument("worldId") { type = NavType.LongType }),
        ) { backStackEntry ->
            CharacterListScreen(
                worldId = backStackEntry.arguments?.getLong("worldId") ?: 0L,
                onConnect = { host, port, characterName, characterPassword, connectWorldId, connectWorldName ->
                    navController.navigateToSession(host, port, characterName, characterPassword, connectWorldId, connectWorldName)
                },
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenWorlds = {
                    navController.navigate(ROUTE_WORLDS) {
                        popUpTo(ROUTE_WORLDS) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenPlayers = { navController.navigate(ROUTE_PLAYERS) },
                onOpenTriggers = { navController.navigate(ROUTE_TRIGGERS) },
                onOpenSlashCommands = { navController.navigate(ROUTE_SLASH_COMMANDS) },
                onOpenHelp = { navController.navigate(ROUTE_HELP) },
            )
        }
        composable(ROUTE_PLAYERS) {
            PlayersScreen(
                onBack = { navController.popBackStack() },
                onConnect = { host, port, characterName, characterPassword, connectWorldId, connectWorldName ->
                    navController.navigateToSession(host, port, characterName, characterPassword, connectWorldId, connectWorldName)
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_TRIGGERS) {
            TriggerListScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_SLASH_COMMANDS) {
            SlashCommandListScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(ROUTE_HELP) {
            HelpScreen(
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(
            route = ROUTE_SESSION,
            arguments = listOf(
                navArgument("host") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("port") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("charName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("charPass") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("worldId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("worldName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            SessionScreen(
                initialHost = backStackEntry.arguments?.getString("host"),
                initialPort = backStackEntry.arguments?.getString("port"),
                worldName = backStackEntry.arguments?.getString("worldName"),
                autoLoginCharacterName = backStackEntry.arguments?.getString("charName"),
                autoLoginCharacterPassword = backStackEntry.arguments?.getString("charPass"),
                worldId = backStackEntry.arguments?.getString("worldId")?.toLongOrNull(),
                onNavigateToWorlds = {
                    // Deliberately no popUpTo here — unlike Settings' onOpenWorlds (which has
                    // nothing worth returning to), this Session entry has live tabs. Popping it
                    // would make it unreachable except by opening a brand-new tab; a plain push
                    // keeps it in the back stack so Back (or re-entering Session) restores it,
                    // same ViewModel and all.
                    navController.navigate(ROUTE_WORLDS)
                },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
    }
}
