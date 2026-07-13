package com.rickd.mushclient.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rickd.mushclient.ui.session.SessionScreen
import com.rickd.mushclient.ui.settings.SettingsScreen
import com.rickd.mushclient.ui.worlds.CharacterListScreen
import com.rickd.mushclient.ui.worlds.WorldListScreen

private const val ROUTE_WORLDS = "worlds"
private const val ROUTE_CHARACTERS = "characters/{worldId}"
private const val ROUTE_SESSION = "session?host={host}&port={port}&charName={charName}&charPass={charPass}"
private const val ROUTE_SETTINGS = "settings"

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
                    val charArgs = if (characterName != null && characterPassword != null) {
                        "&charName=${Uri.encode(characterName)}&charPass=${Uri.encode(characterPassword)}"
                    } else {
                        ""
                    }
                    navController.navigate("session?host=${Uri.encode(host)}&port=$port$charArgs")
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
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
