package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.FutureParsingPromptState
import com.pennywiseai.tracker.ui.theme.Spacing

@Composable
fun FutureParsingPromptDialog(
    prompt: FutureParsingPromptState,
    onConfirm: (extraBodyAliasSources: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedExtras = remember(
        prompt.rawMerchantName,
        prompt.displayMerchantName,
        prompt.optionalBodyAliasSources,
    ) {
        mutableStateListOf<String>().apply { addAll(prompt.optionalBodyAliasSources) }
    }

    val message = when {
        prompt.merchantChanged && prompt.categoryChanged ->
            stringResource(
                R.string.future_parsing_prompt_both,
                prompt.rawMerchantName,
                prompt.displayMerchantName,
                prompt.category,
            )
        prompt.merchantChanged ->
            stringResource(
                R.string.future_parsing_prompt_merchant,
                prompt.rawMerchantName,
                prompt.displayMerchantName,
                prompt.category,
            )
        else ->
            stringResource(
                R.string.future_parsing_prompt_category,
                prompt.displayMerchantName,
                prompt.category,
            )
    }

    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.future_parsing_prompt_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                prompt.smsSnippet?.let { snip ->
                    Text(
                        text = stringResource(R.string.future_parsing_sms_context_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = snip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (prompt.optionalBodyAliasSources.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.future_parsing_optional_aliases_intro),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    for (label in prompt.optionalBodyAliasSources) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selectedExtras.contains(label),
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedExtras.contains(label)) selectedExtras.add(label)
                                    } else {
                                        selectedExtras.remove(label)
                                    }
                                },
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedExtras.toList()) }) {
                Text(stringResource(R.string.future_parsing_prompt_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.future_parsing_prompt_dismiss))
            }
        },
    )
}
