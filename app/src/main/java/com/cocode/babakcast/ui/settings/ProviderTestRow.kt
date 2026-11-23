package com.cocode.babakcast.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.R
import com.cocode.babakcast.data.remote.ProviderCheckOutcome
import com.cocode.babakcast.ui.theme.BabakCastColors

/** The Test button in the provider dialog and, under it, what the test found. */
@Composable
internal fun ProviderTestRow(state: ProviderTestState, inputs: ProviderTestInputs, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onTest,
            enabled = inputs.apiKey.isNotBlank() && !state.running,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BabakCastColors.PrimaryAccent)
        ) {
            Text(
                stringResource(if (state.running) R.string.provider_test_running else R.string.provider_test),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        state.outcomeFor(inputs)?.let { outcome ->
            Text(
                text = outcome.message(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (outcome is ProviderCheckOutcome.Works) BabakCastColors.Success else BabakCastColors.Error
            )
        }
    }
}

@Composable
private fun ProviderCheckOutcome.message(): String = when (this) {
    // The resource formats the seconds in the app's locale (%1$.1f).
    is ProviderCheckOutcome.Works -> stringResource(R.string.provider_test_works, elapsedMs / 1000.0)
    ProviderCheckOutcome.Unreadable -> stringResource(R.string.provider_test_unreadable)
    ProviderCheckOutcome.KeyRejected -> stringResource(R.string.provider_test_key_rejected)
    ProviderCheckOutcome.NotFound -> stringResource(R.string.provider_test_not_found)
    ProviderCheckOutcome.UsageLimit -> stringResource(R.string.provider_test_usage_limit)
    is ProviderCheckOutcome.Refused -> stringResource(R.string.provider_test_refused, code)
    ProviderCheckOutcome.Unreachable -> stringResource(R.string.provider_test_unreachable)
}
