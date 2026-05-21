package net.number42.dutchtrains.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.util.HashMap
import net.number42.dutchtrains.domain.model.TrainMaterial
import net.number42.dutchtrains.ui.screen.home.HomeScreen
import net.number42.dutchtrains.ui.screen.settings.SettingsScreen
import net.number42.dutchtrains.ui.screen.trip.TripDetailScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object TripDetail : Screen("trip/{ctxRecon}") {
        fun createRoute(ctxRecon: String): String = "trip/${Uri.encode(ctxRecon)}"
    }
}

@Composable
fun AppNavigation(startDestination: String = Screen.Home.route) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToTripDetail = { ctxRecon, materials ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("tripMaterials", HashMap<String, TrainMaterial>(materials))
                    navController.navigate(Screen.TripDetail.createRoute(ctxRecon))
                },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        // Settings was the start destination (first launch) — go to Home
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Settings.route) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(
            route = Screen.TripDetail.route,
            arguments = listOf(navArgument("ctxRecon") { type = NavType.StringType }),
        ) {
            TripDetailScreen(
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.TripDetail.route) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
