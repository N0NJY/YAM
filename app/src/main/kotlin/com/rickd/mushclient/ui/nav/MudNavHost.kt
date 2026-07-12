package com.rickd.mushclient.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rickd.mushclient.ui.profiles.ProfileListScreen
import com.rickd.mushclient.ui.session.SessionScreen
import com.rickd.mushclient.ui.settings.SettingsScreen

private const val ROUTE_PROFILES = "profiles"
private const val ROUTE_SESSION = "session?host={host}&port={port}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun MudNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_PROFILES) {
        composable(ROUTE_PROFILES) {
            ProfileListScreen(
                onConnect = { profile ->
                    navController.navigate("session?host=${Uri.encode(profile.host)}&port=${profile.port}")
                },
                onConnectManually = { navController.navigate("session") },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
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
            ),
        ) { backStackEntry ->
            SessionScreen(
                initialHost = backStackEntry.arguments?.getString("host"),
                initialPort = backStackEntry.arguments?.getString("port"),
            )
        }
    }
}
