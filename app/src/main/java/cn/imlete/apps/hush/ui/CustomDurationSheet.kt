package cn.imlete.apps.hush.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.ui.components.DragHandle
import cn.imlete.apps.hush.ui.components.SheetContainer
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.AmberSoft
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.LabelSize
import cn.imlete.apps.hush.ui.theme.Muted
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/**
 * Drawer 2 · custom hour/minute wheels —— a port of CustomDurationSheet.kt:57-242.
 * The initial values [initialHours] / [initialMinutes] are passed in by MainActivity: converted from the stored custom duration,
 * or 0/0 when nothing is stored (opening shows 00:00; spec 2026-10-05-drawer-copy-defaults §①).
 * Column width 120dp, row height 44dp, 5 rows visible (220dp); top/bottom contentPadding = 44 × (5 / 2) = 88dp.
 * The wheel's centered item is reported via [Wheel.onSelect]; when confirming, [onConfirm] returns hours / minutes.
 * At 00:00 "Confirm" turns [Muted] and is not clickable (spec 2026-10-05-idle-start-opens-drawer §3.5).
 */
@Composable
fun CustomDurationSheet(
    visible: Boolean,
    initialHours: Int,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (hours: Int, minutes: Int) -> Unit,
) {
    SheetContainer(visible, onDismiss) {
        DragHandle()
        var hours by remember { mutableIntStateOf(initialHours) }
        var minutes by remember { mutableIntStateOf(initialMinutes) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.action_cancel),
                color = Muted,
                fontSize = LabelSize,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            // Centered 00:00 → confirm disabled; scroll one notch to 00:01 and it immediately re-enables (spec 2026-10-05-idle-start-opens-drawer §3.5)
            val confirmEnabled = hours > 0 || minutes > 0
            Text(
                text = stringResource(R.string.action_confirm),
                color = if (confirmEnabled) Amber else Muted,
                fontSize = LabelSize,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(enabled = confirmEnabled, onClick = { onConfirm(hours, minutes) })
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Wheel(valueCount = 24, selected = hours, onSelect = { hours = it })
            Spacer(Modifier.width(8.dp))
            Wheel(valueCount = 60, selected = minutes, onSelect = { minutes = it })
        }
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * Single-column wheel. While scrolling it reports the centered item's index via [onSelect] (deduplicated with [selected] to avoid repeated callbacks);
 * selected state = amber text + 28sp + Semibold, unselected = Muted + 22sp.
 */
@Composable
private fun Wheel(
    valueCount: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val flingBehavior = rememberSnapFlingBehavior(listState) // UI_SPEC §3.5: snaps back to a whole row once it settles
    var lastReported by remember(selected) { mutableIntStateOf(selected) }
    LaunchedEffect(listState) {
        // 1) After the first layout, align the initially selected item to the viewport center: the contentPadding
        //    semantics leave it uncertain whether selected lands at y=0 or y=88, so a delta scroll covers it,
        //    guaranteeing centered on open with no +2 jump (spec §3.1).
        val first = snapshotFlow { listState.layoutInfo }
            .first { it.visibleItemsInfo.isNotEmpty() }
        val center0 = (first.viewportStartOffset + first.viewportEndOffset) / 2
        val sel = first.visibleItemsInfo.firstOrNull { it.index == selected }
        if (sel != null) {
            val delta = (sel.offset + sel.size / 2) - center0
            if (delta != 0) listState.scroll { scrollBy(delta.toFloat()) }
        }
        // 2) Start reporting the centered item only after alignment completes; on the first frame centered == selected,
        //    so a mistaken callback cannot trigger a jump.
        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                val centered = info.visibleItemsInfo.minByOrNull {
                    abs(it.offset + it.size / 2 - center)
                }
                if (centered != null && centered.index != lastReported) {
                    lastReported = centered.index
                    onSelect(centered.index)
                }
            }
    }
    Box(
        modifier = Modifier.width(120.dp).height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(AmberSoft, RoundedCornerShape(10.dp)),
        )
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(valueCount) { index ->
                val isSelected = index == selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .graphicsLayer {
                            // Edge fade (spec §3.2): the farther an item is from the viewport center, the fainter and smaller;
                            // the scroll state is read during drawing, without triggering per-frame recomposition.
                            val info = listState.layoutInfo
                            val me = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
                            val d = if (me != null) abs((me.offset + me.size / 2 - center).toFloat()) else 0f
                            val t = (d / (2.5f * 44.dp.toPx())).coerceIn(0f, 1f)
                            alpha = 1f - t * 0.7f
                            scaleX = 1f - t * 0.12f
                            scaleY = scaleX
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (index < 10) "0$index" else "$index",
                        color = if (isSelected) Amber else Muted,
                        fontSize = if (isSelected) 28.sp else 22.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 420)
@Composable
private fun CustomDurationSheetPreview() {
    HushTheme { CustomDurationSheet(true, 0, 0, {}, { _, _ -> }) }
}
