package cn.imlete.apps.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.timer.Choice
import cn.imlete.apps.hush.ui.components.DragHandle
import cn.imlete.apps.hush.ui.components.SheetContainer
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.HeadlineSize
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.ui.theme.TitleSize
import cn.imlete.apps.hush.util.DurationFormatter

/**
 * Drawer 1 · duration —— a port of DurationSheet.kt:109-144 (originally "Set countdown duration", renamed per spec §②).
 * Selected state = amber text + Semibold + a 3×20dp bar on the left, no checkmark.
 */
@Composable
fun DurationSheet(
    visible: Boolean,
    choice: Choice,
    onDismiss: () -> Unit,
    onSelect: (Choice) -> Unit,
    onCustom: () -> Unit,
) {
    SheetContainer(visible, onDismiss) {
        DragHandle()
        Text(
            text = stringResource(R.string.duration_title),
            color = OnInk,
            fontSize = HeadlineSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
        )
        SheetOptionRow(
            label = stringResource(R.string.duration_off),
            selected = choice == Choice.Off,
            onClick = { onSelect(Choice.Off) },
        )
        SheetOptionRow(
            label = "15:00",
            selected = choice == Choice.Preset(15 * 60_000L),
            onClick = { onSelect(Choice.Preset(15 * 60_000L)) },
        )
        SheetOptionRow(
            label = "30:00",
            selected = choice == Choice.Preset(30 * 60_000L),
            onClick = { onSelect(Choice.Preset(30 * 60_000L)) },
        )
        SheetOptionRow(
            label = "60:00",
            selected = choice == Choice.Preset(60 * 60_000L),
            onClick = { onSelect(Choice.Preset(60 * 60_000L)) },
        )
        // Only when custom is currently selected does the row carry a duration: >60 minutes HH:MM:SS, ≤60 minutes (including exactly 60) MM:SS (spec §④)
        val customMs = (choice as? Choice.Custom)?.durationMs
        SheetOptionRow(
            label = if (customMs != null) {
                stringResource(
                    R.string.duration_custom_value,
                    DurationFormatter.formatCountdown(isCustom = true, totalMs = customMs, valueMs = customMs),
                )
            } else {
                stringResource(R.string.duration_custom)
            },
            selected = choice is Choice.Custom,
            onClick = onCustom,
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SheetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .background(
                    if (selected) Amber else Color.Transparent,
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            color = if (selected) Amber else Muted,
            fontSize = TitleSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 560)
@Composable
private fun DurationSheetPreview() {
    HushTheme { DurationSheet(true, Choice.Preset(30 * 60_000L), {}, {}, {}) }
}
