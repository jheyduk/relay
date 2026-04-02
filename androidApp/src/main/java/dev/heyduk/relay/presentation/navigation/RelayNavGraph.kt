package dev.heyduk.relay.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import dev.heyduk.relay.presentation.chat.ChatScreen
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
                },
                onNavigateToChat = { kuerzel ->
                    navController.navigate("chat/$kuerzel")
                }
            )
        }
        composable(
            "chat/{kuerzel}",
            deepLinks = listOf(navDeepLink { uriPattern = "relay://chat/{kuerzel}" })
        ) { backStackEntry ->
            val kuerzel = backStackEntry.arguments?.getString("kuerzel") ?: return@composable
            ChatScreen(
                kuerzel = kuerzel,
                onNavigateBack = { navController.popBackStack() }
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
