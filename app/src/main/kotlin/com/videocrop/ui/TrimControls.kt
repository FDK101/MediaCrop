package com.videocrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.videocrop.ui.theme.*

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val millis = ms % 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}

@Composable
fun TrimControls(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "TRIM",
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Range slider for start/end
        if (durationMs > 0) {
            RangeSliderRow(
                durationMs = durationMs,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                currentPositionMs = currentPositionMs,
                onTrimStartChanged = onTrimStartChanged,
                onTrimEndChanged = onTrimEndChanged,
                onSeek = onSeek
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start time controls
        TimeAdjustRow(
            label = "IN",
            timeMs = trimStartMs,
            onAdjust = { delta -> onTrimStartChanged((trimStartMs + delta).coerceIn(0L, trimEndMs - 33L)) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // End time controls
        TimeAdjustRow(
            label = "OUT",
            timeMs = trimEndMs,
            onAdjust = { delta -> onTrimEndChanged((trimEndMs + delta).coerceIn(trimStartMs + 33L, durationMs)) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Duration display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Duration",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurfaceVariant
            )
            Text(
                text = formatTime(trimEndMs - trimStartMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = Secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeSliderRow(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    currentPositionMs: Long,
    onTrimStartChanged: (Long) -> Unit,
    onTrimEndChanged: (Long) -> Unit,
    onSeek: (Long) -> Unit
) {
    Column {
        // Current position indicator
        Text(
            text = "▶  ${formatTime(currentPositionMs)}",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Seek slider for current position
        Slider(
            value = currentPositionMs.toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Secondary,
                activeTrackColor = Secondary,
                inactiveTrackColor = TrackInactiveColor
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Trim range slider
        RangeSlider(
            value = trimStartMs.toFloat()..trimEndMs.toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong()
                val newEnd = range.endInclusive.toLong()
                if (newStart != trimStartMs) onTrimStartChanged(newStart)
                if (newEnd != trimEndMs) onTrimEndChanged(newEnd)
            },
            valueRange = 0f..durationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = ThumbColor,
                activeTrackColor = TrackActiveColor,
                inactiveTrackColor = TrackInactiveColor
            )
        )

        // Start/End time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(trimStartMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = ThumbColor
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = OnSurfaceVariant
            )
            Text(
                text = formatTime(trimEndMs),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = ThumbColor
            )
        }
    }
}

@Composable
private fun TimeAdjustRow(
    label: String,
    timeMs: Long,
    onAdjust: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )

        Text(
            text = formatTime(timeMs),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp
            ),
            color = OnBackground,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        AdjustButton("-1s") { onAdjust(-1000L) }
        AdjustButton("-100") { onAdjust(-100L) }
        AdjustButton("-1") { onAdjust(-1L) }
        Spacer(modifier = Modifier.width(4.dp))
        AdjustButton("+1") { onAdjust(1L) }
        AdjustButton("+100") { onAdjust(100L) }
        AdjustButton("+1s") { onAdjust(1000L) }
    }
}

@Composable
private fun AdjustButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Primary
        )
    }
}
