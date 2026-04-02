package dev.heyduk.relay.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.heyduk.relay.domain.model.ChatMessage

/**
 * Interactive question card for QUESTION-type messages.
 * Parses options from the message content (first line = question, remaining = options).
 * Renders options as selectable chips. Highlights the selected option when answered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    message: ChatMessage,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnswered = message.callbackResponse != null
    val (question, options) = parseQuestionAndOptions(message.content)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Session label
            Text(
                text = "@${message.session}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Question text
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (options.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Option chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = message.callbackResponse == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!isAnswered) onOptionSelected(option) },
                            label = { Text(option) },
                            enabled = !isAnswered || isSelected,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parses a question message into question text and option list.
 * Convention: first line is the question, remaining non-empty lines are options.
 * Falls back to pipe-separated options if only a single line with pipes.
 */
internal fun parseQuestionAndOptions(content: String): Pair<String, List<String>> {
    val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

    if (lines.size <= 1) {
        // Single line: try pipe-separated options
        val parts = content.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size > 1) {
            parts.first() to parts.drop(1)
        } else {
            content.trim() to emptyList()
        }
    }

    // Multi-line: first line is question, rest are options
    return lines.first() to lines.drop(1)
}
