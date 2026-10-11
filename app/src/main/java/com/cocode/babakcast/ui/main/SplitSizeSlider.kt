package com.cocode.babakcast.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocode.babakcast.ui.theme.BabakCastColors
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.cocode.babakcast.R

@Composable
internal fun SplitSizeSlider(
    valueMb: Int,
    minMb: Int,
    maxMb: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.split_size_label),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.size_megabytes, valueMb),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = BabakCastColors.PrimaryAccent
            )
        }
        Slider(
            value = valueMb.toFloat(),
            onValueChange = {
                val snapped = it.roundToInt().coerceIn(minMb, maxMb)
                if (snapped != valueMb) onValueChange(snapped)
            },
            valueRange = minMb.toFloat()..maxMb.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = BabakCastColors.PrimaryAccent,
                activeTrackColor = BabakCastColors.PrimaryAccent,
                inactiveTrackColor = BabakCastColors.PrimaryAccent.copy(alpha = 0.25f)
            )
        )
    }
}

