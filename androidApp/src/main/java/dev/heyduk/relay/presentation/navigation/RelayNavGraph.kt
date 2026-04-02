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
 * Routes to setup screen if tokens are not configured, otherwise to session list screen.
 */
@Composable
fun RelayNavGraph(navController: NavHostController, isConfigured: Boolean) {
    NavHost(
        navController = navController,
        startDestination = if (isConfigured) "sessions" else "setup"
    ) {
        composable("setup") {
            SetupScreen(
                onConfigured = {
                    navController.navigate("sessions") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("sessions") {
            SessionListScreen(
                onNavigateToSetup = {
                    navController.navigate("setup")
                }
            )
        }
        composable("status") {
            StatusScreen(
                onNavigateToSetup = {
                    navController.navigate("setup")
                }
            )
        }
    }
}
