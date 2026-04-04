package dev.heyduk.relay.presentation.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Fullscreen dialog for creating a new Claude Code session.
 * Phases: Loading -> Directory Selection (with fuzzy search) -> Custom Path -> Confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onSessionCreated: (String) -> Unit,
    viewModel: CreateSessionViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load directories when dialog opens
    LaunchedEffect(Unit) {
        viewModel.loadDirectories()
    }

    // Navigate on successful session creation
    LaunchedEffect(uiState.createdKuerzel) {
        uiState.createdKuerzel?.let { kuerzel ->
            onSessionCreated(kuerzel)
        }
    }

    // Show errors via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("New Session") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (uiState.phase) {
                    CreateSessionViewModel.DialogPhase.LOADING -> LoadingPhase()
                    CreateSessionViewModel.DialogPhase.DIRECTORY_SELECTION -> DirectorySelectionPhase(
                        uiState = uiState,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onDirectorySelected = viewModel::onDirectorySelected,
                        onCustomPathSelected = viewModel::onCustomPathSelected
                    )
                    CreateSessionViewModel.DialogPhase.CUSTOM_PATH -> CustomPathPhase(
                        onBack = viewModel::onBackToDirectorySelection,
                        onConfirm = viewModel::onCustomPathConfirmed
                    )
                    CreateSessionViewModel.DialogPhase.CONFIRMATION -> ConfirmationPhase(
                        uiState = uiState,
                        onKuerzelChanged = viewModel::onKuerzelChanged,
                        onFlagsToggled = viewModel::onFlagsToggled,
                        onBack = viewModel::onBackToDirectorySelection,
                        onCreate = viewModel::onCreateSession
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingPhase() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading directories...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DirectorySelectionPhase(
    uiState: CreateSessionViewModel.CreateSessionUiState,
    onSearchQueryChanged: (String) -> Unit,
    onDirectorySelected: (dev.heyduk.relay.domain.model.DirectoryEntry) -> Unit,
    onCustomPathSelected: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search directories...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        if (uiState.filteredDirectories.isEmpty() && uiState.searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No matching directories",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.filteredDirectories) { entry ->
                    ListItem(
                        headlineContent = {
                            Text(entry.name, fontWeight = FontWeight.Bold)
                        },
                        supportingContent = {
                            Text(
                                entry.path,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.clickable { onDirectorySelected(entry) }
                    )
                }
                // Custom path option at bottom
                item {
                    ListItem(
                        headlineContent = { Text("Custom Path...") },
                        leadingContent = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onCustomPathSelected() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomPathPhase(
    onBack: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pathInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = pathInput,
            onValueChange = { pathInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Project path") },
            placeholder = { Text("/Users/...") },
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { onConfirm(pathInput) },
                enabled = pathInput.isNotBlank()
            ) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun ConfirmationPhase(
    uiState: CreateSessionViewModel.CreateSessionUiState,
    onKuerzelChanged: (String) -> Unit,
    onFlagsToggled: () -> Unit,
    onBack: () -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Path display
        Text(
            text = "Project path",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiState.selectedPath,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Kuerzel input
        OutlinedTextField(
            value = uiState.kuerzel,
            onValueChange = onKuerzelChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Session name (kuerzel)") },
            isError = uiState.kuerzelError != null,
            singleLine = true
        )
        if (uiState.kuerzelError != null) {
            Text(
                text = uiState.kuerzelError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        // Flags toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = uiState.flagsEnabled,
                onCheckedChange = { onFlagsToggled() }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "--dangerously-skip-permissions",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Progress indicator
        if (uiState.isCreating) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCreate,
                enabled = !uiState.isCreating
            ) {
                Text("Create")
            }
        }
    }
}
