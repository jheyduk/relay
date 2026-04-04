package dev.heyduk.relay.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.QuestionData

/**
 * Interactive question card for QUESTION-type messages.
 * Supports three modes based on [ChatMessage.questionData]:
 * - Single-choice: tap an option to answer immediately
 * - Multi-choice: toggle options, then tap Submit
 * - Free-text: type a custom response (via "Other" or when no options exist)
 *
 * Falls back to parsing options from message text if questionData is null.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionCard(
    message: ChatMessage,
    onAnswer: (type: String, selections: List<Int>, text: String?, optionCount: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isAnswered = message.callbackResponse != null
    val questionData = message.questionData

    // Derive question text and options from structured data or fallback parsing
    val questionText: String
    val options: List<String>
    val multiSelect: Boolean

    if (questionData != null) {
        val prefix = if (questionData.header != null) "${questionData.header}: " else ""
        questionText = "$prefix${questionData.question}"
        options = questionData.options.map { opt ->
            if (opt.description != null) "${opt.label} -- ${opt.description}" else opt.label
        }
        multiSelect = questionData.multiSelect
    } else {
        // Fallback: parse from message content
        val (q, opts) = parseQuestionAndOptions(message.content)
        questionText = q
        options = opts
        multiSelect = false
    }

    val optionCount = options.size

    // Local state for multi-select toggles
    var selectedIndices by remember { mutableStateOf(setOf<Int>()) }
    var showOtherInput by remember { mutableStateOf(false) }
    var otherText by remember { mutableStateOf("") }

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

            // Context from last assistant output (if available)
            val contextText = questionData?.context
            if (!contextText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = contextText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 8,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Question text
            Text(
                text = questionText,
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
                    options.forEachIndexed { index, option ->
                        val oneBasedIndex = index + 1
                        val isSelected = if (isAnswered) {
                            message.callbackResponse?.contains("#$oneBasedIndex") == true
                        } else {
                            oneBasedIndex in selectedIndices
                        }

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isAnswered) {
                                    if (multiSelect) {
                                        // Toggle selection
                                        selectedIndices = if (oneBasedIndex in selectedIndices) {
                                            selectedIndices - oneBasedIndex
                                        } else {
                                            selectedIndices + oneBasedIndex
                                        }
                                        showOtherInput = false
                                    } else {
                                        // Single-choice: answer immediately
                                        onAnswer("single", listOf(oneBasedIndex), null, optionCount)
                                    }
                                }
                            },
                            label = { Text("$oneBasedIndex. $option") },
                            enabled = !isAnswered || isSelected,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    // "Other" chip for free-text input
                    if (!isAnswered) {
                        FilterChip(
                            selected = showOtherInput,
                            onClick = {
                                showOtherInput = !showOtherInput
                                if (showOtherInput) selectedIndices = emptySet()
                            },
                            label = { Text("Other...") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                }

                // Multi-select Submit button
                if (multiSelect && !isAnswered && selectedIndices.isNotEmpty() && !showOtherInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onAnswer("multi", selectedIndices.sorted(), null, optionCount)
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Submit")
                    }
                }
            }

            // Free-text input (when "Other" is selected or no options exist)
            if (!isAnswered && (showOtherInput || options.isEmpty())) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        placeholder = { Text("Type your answer...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (otherText.isNotBlank()) {
                                onAnswer("text", emptyList(), otherText, optionCount)
                            }
                        },
                        enabled = otherText.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Send")
                    }
                }
            }

            // Show answered state
            if (isAnswered) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Answered: ${message.callbackResponse}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
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
