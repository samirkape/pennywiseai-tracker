package com.pennywiseai.tracker.presentation.transactions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.domain.model.FutureParsingPromptState

@Composable
fun FutureParsingPromptDialog(
    prompt: FutureParsingPromptState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.future_parsing_prompt_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
