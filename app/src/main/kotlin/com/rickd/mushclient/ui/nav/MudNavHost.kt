package com.rickd.mushclient.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rickd.mushclient.ui.players.PlayersScreen
import com.rickd.mushclient.ui.session.SessionScreen
import com.rickd.mushclient.ui.settings.SettingsScreen
import com.rickd.mushclient.ui.worlds.CharacterListScreen
import com.rickd.mushclient.ui.worlds.WorldListScreen

private const val ROUTE_WORLDS = "worlds"
private const val ROUTE_CHARACTERS = "characters/{worldId}"
private const val ROUTE_SESSION = "session?host={host}&port={port}&charName={charName}&charPass={charPass}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_PLAYERS = "players"

private fun NavController.navigateToSession(host: String, port: Int, characterName: String?, characterPassword: String?) {
    val charArgs = if (characterName != null && characterPassword != null) {
        "&charName=${Uri.encode(characterName)}&charPass=${Uri.encode(characterPassword)}"
    } else {
        ""
    }
    navigate("session?host=${Uri.encode(host)}&port=$port$charArgs")
}

@Composable
fun MudNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_WORLDS) {
        composable(ROUTE_WORLDS) {
            WorldListScreen(
                onSelectWorld = { world -> navController.navigate("characters/${world.id}") },
                onConnectManually = { navController.navigate("session") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
            )
        }
        composable(
            route = ROUTE_CHARACTERS,
            arguments = listOf(navArgument("worldId") { type = NavType.LongType }),
        ) { backStackEntry ->
            CharacterListScreen(
                worldId = backStackEntry.arguments?.getLong("worldId") ?: 0L,
                onConnect = { host, port, characterName, characterPassword ->
                    navController.navigateToSession(host, port, characterName, characterPassword)
                },
                onBack = { navController.popBackStack() },
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
            )
        }
        composable(ROUTE_PLAYERS) {
            PlayersScreen(
                onBack = { navController.popBackStack() },
                onConnect = { host, port, characterName, characterPassword ->
                    navController.navigateToSession(host, port, characterName, characterPassword)
                },
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
            ),
        ) { backStackEntry ->
            SessionScreen(
                initialHost = backStackEntry.arguments?.getString("host"),
                initialPort = backStackEntry.arguments?.getString("port"),
                autoLoginCharacterName = backStackEntry.arguments?.getString("charName"),
                autoLoginCharacterPassword = backStackEntry.arguments?.getString("charPass"),
            )
        }
    }
}
