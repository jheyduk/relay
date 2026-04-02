package dev.heyduk.relay.presentation.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.heyduk.relay.presentation.components.CommandInput
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Main session list screen with pull-to-refresh, FAB, drawer navigation,
 * and bottom command input bar.
 *
 * Layout: ModalNavigationDrawer > Scaffold (correct nesting per research pitfall 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToSetup: () -> Unit,
    viewModel: SessionListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                uiState.sessions.forEach { session ->
                    NavigationDrawerItem(
                        label = { Text("@${session.kuerzel}") },
                        selected = session.kuerzel == uiState.selectedKuerzel,
                        onClick = {
                            viewModel.selectSession(session.kuerzel)
                            scope.launch { drawerState.close() }
                        },
                        badge = { SessionStatusChip(status = session.status) }
                    )
                }
            }
        },
        drawerState = drawerState
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Relay") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToSetup) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            bottomBar = {
                CommandInput(
                    selectedKuerzel = uiState.selectedKuerzel,
                    onSendCommand = viewModel::handleCommandInput,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = viewModel::refreshSessions) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh sessions")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::refreshSessions,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (uiState.sessions.isEmpty() && !uiState.isRefreshing) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No sessions.\nPull to refresh or tap the refresh button.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.sessions, key = { it.kuerzel }) { session ->
                        SessionCard(
                            session = session,
                            lastResponse = uiState.lastResponses[session.kuerzel],
                            isExpanded = session.kuerzel in uiState.expandedCards,
                            onToggleExpand = { viewModel.toggleCardExpanded(session.kuerzel) },
                            onSelect = {
                                viewModel.selectSession(session.kuerzel)
                                // Fetch /last response if not loaded yet
                                if (session.kuerzel !in uiState.lastResponses) {
                                    viewModel.fetchLastResponse(session.kuerzel)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
