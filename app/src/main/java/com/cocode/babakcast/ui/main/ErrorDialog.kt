package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors
import com.cocode.babakcast.util.AppError
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ErrorDialog(
    error: AppError,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        shape = MaterialTheme.shapes.medium,
        color = BabakCastColors.Error.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = error.title(context),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = BabakCastColors.Error
            )

            Text(
                text = error.message(context),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            error.fixHint(context)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        stringResource(R.string.action_dismiss),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
