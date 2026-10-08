package cn.imlete.apps.hush.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.BodySize
import cn.imlete.apps.hush.ui.theme.HeadlineSize
import cn.imlete.apps.hush.ui.theme.LabelSize
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.ui.theme.Surface
import cn.imlete.apps.hush.ui.theme.Track
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** M3 ModalBottomSheet default scrim rgba(0,0,0,.32) */
@Composable
fun Scrim(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
    )
}

/**
 * Bottom-sheet container: scrim fades in/out over 250ms + the sheet slides up in/out down over 250ms (spec §3.3).
 * [visible] is driven by the outer overlay state; once hidden and the exit animation has finished, the content is no longer composed,
 * so reopening starts with fresh internal state (consistent with the old when-branch behavior).
 */
@Composable
fun SheetContainer(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val thresholdPx = with(LocalDensity.current) { 96.dp.toPx() }
    val flingVelocityPx = with(LocalDensity.current) { 800.dp.toPx() }
    // Reset before opening: after a previous drag-close, offsetY sits off-screen; the reset happens before the content is composed, leaving no visual residue
    LaunchedEffect(visible) { if (visible) offsetY = 0f }
    val sheetSpec = tween<IntOffset>(250, easing = FastOutSlowInEasing)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(250, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(250, easing = FastOutSlowInEasing)),
        ) {
            Scrim(onDismiss)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(sheetSpec) { it },
            exit = slideOutVertically(sheetSpec) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .draggable(
                        state = rememberDraggableState { delta ->
                            offsetY = (offsetY + delta).coerceAtLeast(0f) // Drag downward only, clamped at 0
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (offsetY >= thresholdPx || velocity >= flingVelocityPx) {
                                // Threshold met: slide off-screen from the current offset, then trigger dismissal (spec §3.4; the exit animation only applies to content already off-screen)
                                scope.launch {
                                    animate(
                                        initialValue = offsetY,
                                        targetValue = sheetHeightPx,
                                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                                    ) { v, _ -> offsetY = v }
                                    onDismiss()
                                }
                            } else {
                                scope.launch {
                                    animate(
                                        initialValue = offsetY,
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    ) { v, _ -> offsetY = v }
                                }
                            }
                        },
                    )
                    .background(Surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .navigationBarsPadding(),
                content = content,
            )
        }
    }
}

@Composable
fun ColumnScope.DragHandle() {
    Box(
        Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 12.dp)
            .width(36.dp)
            .height(4.dp)
            .background(Track, RoundedCornerShape(2.dp)),
    )
}

@Composable
fun DialogContainer(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Scrim(onDismiss)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(328.dp)
                .background(Surface, RoundedCornerShape(28.dp))
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
fun HushDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DialogContainer(onDismiss) {
        Text(title, color = OnInk, fontSize = HeadlineSize, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Muted, fontSize = BodySize, lineHeight = 22.sp)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogButton(dismissLabel, Muted, onDismiss)
            DialogButton(confirmLabel, Amber, onConfirm)
        }
    }
}

/**
 * Guidance dialog: title + body + primary button (+ optional secondary button) + cancel, arranged vertically.
 * "Lock not ready" now only has the single device-admin entry (spec 2026-10-07-remove-accessibility-lock §5), hence the optional secondary button.
 * Tapping the scrim dismisses it.
 */
@Composable
fun HushActionDialog(
    title: String,
    text: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    dismissLabel: String,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    DialogContainer(onDismiss) {
        Text(title, color = OnInk, fontSize = HeadlineSize, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))
        Text(text, color = Muted, fontSize = BodySize, lineHeight = 22.sp)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            DialogButton(primaryLabel, Amber, onPrimary)
            if (secondaryLabel != null && onSecondary != null) {
                DialogButton(secondaryLabel, Amber, onSecondary)
            }
            DialogButton(dismissLabel, Muted, onDismiss)
        }
    }
}

@Composable
private fun DialogButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = color,
        fontSize = LabelSize,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
