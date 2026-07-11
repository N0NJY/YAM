package com.rickd.mushclient.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rickd.mushclient.ui.session.SessionScreen

private const val ROUTE_HOME = "home"

@Composable
fun MudNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            SessionScreen()
        }
    }
}
