package com.cocode.babakcast.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.cocode.babakcast.ui.main.MainScreen
import com.cocode.babakcast.ui.settings.SettingsScreen
import com.cocode.babakcast.ui.share.ShareScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
    object Share : Screen("share/{content}")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToShare = { content ->
                    navController.navigate("share/$content")
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("share/{content}") { backStackEntry ->
            val content = backStackEntry.arguments?.getString("content") ?: ""
            ShareScreen(
                content = content,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
