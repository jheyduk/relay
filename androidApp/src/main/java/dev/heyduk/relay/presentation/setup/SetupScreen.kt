package dev.heyduk.relay.presentation.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Setup screen for entering the server shared secret and optional WireGuard IP.
 * Navigates to the session list screen on successful configuration.
 */
@Composable
fun SetupScreen(
    onConfigured: () -> Unit,
    viewModel: SetupViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate away when successfully configured
    LaunchedEffect(uiState.isConfigured, uiState.validationResult) {
        if (uiState.isConfigured && uiState.validationResult?.contains("saved") == true) {
            onConfigured()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Relay Setup",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.serverSecret,
            onValueChange = viewModel::updateServerSecret,
            label = { Text("Server Secret") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.wireguardIp,
            onValueChange = viewModel::updateWireguardIp,
            label = { Text("Server IP (optional)") },
            placeholder = { Text("e.g., 192.168.1.100") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = viewModel::validateAndSave,
            enabled = !uiState.isValidating,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save")
            }
        }

        uiState.validationResult?.let { result ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = result,
                color = if (result.contains("success", ignoreCase = true))
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
