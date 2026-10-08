package cn.imlete.apps.hush.ui

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.ui.components.CountdownRing
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.AmberDeep
import cn.imlete.apps.hush.ui.theme.BodySize
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.Ink
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.ui.theme.SmallSize
import cn.imlete.apps.hush.ui.theme.TitleSize
import cn.imlete.apps.hush.ui.theme.nightWash

private val HeaderHeight = 48.dp
private val TapTarget = 44.dp
private val IconVisual = 28.dp
private val SideKeySize = 46.dp
private val SideKeyIcon = 22.dp
private val PrimaryKeySize = 82.dp
private val PrimaryKeyIcon = 36.dp
private val HaloSize = 128.dp
private val KeyGap = 24.dp

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenDurationSheet: () -> Unit,
    onOpenSettings: () -> Unit,
    onPrimary: () -> Unit,
    onReduce: () -> Unit,
    onExtend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
                .alpha(state.screenAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onOpenSettings)

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CountdownRing(
                        label = state.centerLabel,
                        subtitle = state.subtitle,
                        progress = state.progress,
                        running = state.running,
                        onClick = onOpenDurationSheet,
                    )
                    // The "ends at" line keeps a fixed 24dp height placeholder, so the ring does not jump when it changes
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().height(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = state.finishAtLabel.orEmpty(),
                            color = Muted,
                            fontSize = BodySize,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = stringResource(R.string.hint_tap_ring),
                        color = Muted,
                        fontSize = SmallSize,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .alpha(if (state.running) 0f else 1f),
                    )
                }
            }

            HomeFooter(
                state = state,
                onPrimary = onPrimary,
                onReduce = onReduce,
                onExtend = onExtend,
            )
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(HeaderHeight), contentAlignment = Alignment.Center) {
        Text(text = "Hush", color = OnInk, fontSize = TitleSize, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(TapTarget)
                .clip(CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.action_open_settings),
                tint = Muted,
                modifier = Modifier.size(IconVisual),
            )
        }
    }
}

@Composable
private fun HomeFooter(
    state: HomeUiState,
    onPrimary: () -> Unit,
    onReduce: () -> Unit,
    onExtend: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SideKey(
            icon = R.drawable.ic_minus,
            contentDescription = stringResource(R.string.label_reduce, state.stepMinutes),
            enabled = state.canReduce,
            onClick = onReduce,
        )
        Spacer(Modifier.width(KeyGap))
        PrimaryKey(action = state.primaryAction, onClick = onPrimary)
        Spacer(Modifier.width(KeyGap))
        SideKey(
            icon = R.drawable.ic_plus,
            contentDescription = stringResource(R.string.label_extend, state.stepMinutes),
            enabled = state.running,
            onClick = onExtend,
        )
    }
}

@Composable
private fun SideKey(
    @DrawableRes icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SideKeySize)
            .clip(CircleShape)
            .border(1.dp, if (enabled) Muted else Muted.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) OnInk else Muted.copy(alpha = 0.6f),
            modifier = Modifier.size(SideKeyIcon),
        )
    }
}

/**
 * Primary key —— a port of HomeScreen.kt:459-510.
 * Two-layer omnidirectional glow: the far layer is heavily blurred and dim; the near layer is lightly blurred and brighter.
 * API 31+ uses Modifier.blur on a solid color disc; 24–30 falls back to a radial gradient (§6.1).
 */
@Composable
private fun PrimaryKey(action: PrimaryAction, onClick: () -> Unit) {
    Box(Modifier.size(HaloSize), contentAlignment = Alignment.Center) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .size(PrimaryKeySize)
                    .blur(26.dp, BlurredEdgeTreatment.Unbounded)
                    .background(Amber, CircleShape)
                    .alpha(0.28f),
            )
            Box(
                Modifier
                    .size(PrimaryKeySize)
                    .blur(13.dp, BlurredEdgeTreatment.Unbounded)
                    .background(Amber, CircleShape)
                    .alpha(0.62f),
            )
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            0f to Amber.copy(alpha = 0.62f),
                            0.34f to Amber.copy(alpha = 0.28f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(PrimaryKeySize)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Amber, AmberDeep)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.34f),
                            0.55f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        CircleShape,
                    ),
            )
            Icon(
                painter = painterResource(
                    if (action == PrimaryAction.PAUSE) R.drawable.ic_pause else R.drawable.ic_play,
                ),
                contentDescription = when (action) {
                    PrimaryAction.PAUSE -> stringResource(R.string.action_pause_short)
                    PrimaryAction.RESUME -> stringResource(R.string.action_resume_short)
                    PrimaryAction.START -> stringResource(R.string.action_start)
                },
                tint = Ink,
                modifier = Modifier.size(PrimaryKeyIcon),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 929)
@Composable
private fun HomeRunningPreview() {
    HushTheme {
        Box(Modifier.fillMaxSize().background(Ink).nightWash()) {
            HomeScreen(HomeUiState.Running, {}, {}, {}, {}, {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 929)
@Composable
private fun HomeNotStartedPreview() {
    HushTheme {
        Box(Modifier.fillMaxSize().background(Ink).nightWash()) {
            HomeScreen(HomeUiState.NotStarted, {}, {}, {}, {}, {})
        }
    }
}
