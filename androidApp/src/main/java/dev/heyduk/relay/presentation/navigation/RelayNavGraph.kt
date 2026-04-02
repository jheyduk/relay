package dev.heyduk.relay.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.heyduk.relay.presentation.session.SessionListScreen
import dev.heyduk.relay.presentation.setup.SetupScreen
import dev.heyduk.relay.presentation.status.StatusScreen

/**
 * Navigation graph for Relay.
 * Routes to setup screen if tokens are not configured, otherwise to status screen.
 */
@Composable
fun RelayNavGraph(navController: NavHostController, isConfigured: Boolean) {
    NavHost(
        navController = navController,
        startDestination = if (isConfigured) "status" else "setup"
    ) {
        composable("setup") {
            SetupScreen(
                onConfigured = {
                    navController.navigate("status") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("status") {
            SessionListScreen(
                onNavigateToSetup = {
                    navController.navigate("setup")
                }
            )
        }
    }
}
