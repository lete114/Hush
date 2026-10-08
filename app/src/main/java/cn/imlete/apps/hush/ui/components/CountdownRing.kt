package cn.imlete.apps.hush.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.imlete.apps.hush.ui.TICK_MS
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.BodySize
import cn.imlete.apps.hush.ui.theme.DisplaySize
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.Track
import cn.imlete.apps.hush.util.centerLabelMetrics
import kotlin.math.cos
import kotlin.math.sin

private val RingSize = 260.dp
private val RingStroke = 14.dp
private val LabelLineHeight = 64.sp

/** Geometry shared by the track arc and the progress arc: stroke width (px), bounding-square topLeft and size. */
private class GaugeArcFrame(val stroke: Float, val topLeft: Offset, val size: Size)

/** Layer 1 (track) and layer 3 (progress arc) live on different Canvases; each computes the same arc geometry once. */
private fun DrawScope.gaugeArcFrame(): GaugeArcFrame {
    val stroke = RingStroke.toPx()
    val radius = (size.minDimension - stroke) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return GaugeArcFrame(
        stroke = stroke,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
    )
}

/**
 * CountdownRing —— a gauge open at the bottom.
 * 270° arc with a 90° gap centered directly below; start angle 135° (lower left) clockwise through the top to 405° (lower right).
 * Remaining = arc length; 11 muted major ticks inside the arc; no needle.
 */
@Composable
fun CountdownRing(
    label: String,
    subtitle: String,
    progress: Float,
    running: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Target progress → a continuously advancing animated value. The tween duration takes TICK_MS, sharing its source with HushApp's refresh period,
    // so the displayed value trails the target by ≤1 beat (tween duration = tick period) without accumulating over time
    // (at 270° sweep, one beat ≈5.4° for the 10-second tier, ≈0.03° for the 30-minute tier); LinearEasing guarantees uniform speed
    // (the default Spring overshoots and rebounds, making the reading wobble). It also smooths the progress jumps on start / ±5 minutes / changing duration while running.
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = TICK_MS.toInt(), easing = LinearEasing),
        label = "ringProgress",
    )
    Box(
        modifier = modifier.size(RingSize),
        contentAlignment = Alignment.Center,
    ) {
        // Layer 1 (bottom-most): the track. It sits on its own layer so the glow layer can lie above it;
        // otherwise the light spilling forward along the track from the head is entirely covered by the Track's opaque color ("the light is blocked by the track").
        Canvas(Modifier.matchParentSize()) {
            val frame = gaugeArcFrame()
            drawArc(
                color = Track,
                startAngle = GaugeStartDeg,
                sweepAngle = GaugeSweepDeg,
                useCenter = false,
                topLeft = frame.topLeft,
                size = frame.size,
                style = Stroke(width = frame.stroke, cap = StrokeCap.Round),
            )
        }

        // Layer 2: the progress arc's glow layer (above the track, below the progress arc, only while running; the outer layer is unclipped, so the glow can extend outward)
        if (running && animatedProgress > 0f) {
            val sweep = gaugeProgressSweepDeg(animatedProgress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlowArc(GaugeStartDeg, sweep, RingStroke, 26.dp, 0.28f)
                GlowArc(GaugeStartDeg, sweep, RingStroke, 13.dp, 0.62f)
            } else {
                GlowArcFallback(GaugeStartDeg, sweep, RingStroke)
            }
        }

        // Layer 3 (top-most): ticks + solid progress arc + center text (the tap hotspot).
        // The ticks stay above the glow layer, so while running they are not tinted amber by the glow (keeping the original look).
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val frame = gaugeArcFrame()
                val radius = (size.minDimension - frame.stroke) / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                val (tickOuter, tickInner) = gaugeTickRadiiPx(
                    radiusPx = radius,
                    strokePx = frame.stroke,
                    gapPx = 4.dp.toPx(),
                    lengthPx = 10.dp.toPx(),
                )
                val tickWidth = 2.dp.toPx()
                GaugeTickAngles.forEach { angleDeg ->
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    drawLine(
                        color = Muted,
                        start = Offset(center.x + tickOuter * cosA, center.y + tickOuter * sinA),
                        end = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                        strokeWidth = tickWidth,
                        cap = StrokeCap.Round,
                    )
                }

                if (running && animatedProgress > 0f) {
                    drawArc(
                        color = Amber,
                        startAngle = GaugeStartDeg,
                        sweepAngle = gaugeProgressSweepDeg(animatedProgress),
                        useCenter = false,
                        topLeft = frame.topLeft,
                        size = frame.size,
                        style = Stroke(width = frame.stroke, cap = StrokeCap.Round),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 5 characters (MM:SS) uses the base 56sp/2sp letter spacing; ≥6 characters (HH:MM:SS) drops to ×0.8 with 0 letter spacing,
                // so 8 characters ≈197dp no longer presses into the 246dp inner-diameter arc (UI_SPEC §8.2-7, design §3.3).
                val metrics = centerLabelMetrics(label)
                Text(
                    text = label,
                    color = if (running) Amber else Muted,
                    fontSize = DisplaySize * metrics.scale,
                    fontWeight = FontWeight.Light,
                    letterSpacing = metrics.trackingSp.sp,
                    lineHeight = LabelLineHeight * metrics.scale,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = subtitle,
                    color = Muted,
                    fontSize = BodySize,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun GlowArc(
    startAngle: Float,
    sweepAngle: Float,
    stroke: Dp,
    blur: Dp,
    alpha: Float,
) {
    Canvas(
        Modifier
            .fillMaxSize()
            .blur(blur, BlurredEdgeTreatment.Unbounded)
    ) {
        val strokePx = stroke.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            color = Amber.copy(alpha = alpha),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun GlowArcFallback(
    startAngle: Float,
    sweepAngle: Float,
    stroke: Dp,
) {
    Canvas(Modifier.fillMaxSize()) {
        val strokePx = stroke.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        drawArc(Amber.copy(alpha = 0.06f), startAngle, sweepAngle, false, topLeft, arcSize,
            style = Stroke(width = strokePx + 24.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Amber.copy(alpha = 0.12f), startAngle, sweepAngle, false, topLeft, arcSize,
            style = Stroke(width = strokePx + 12.dp.toPx(), cap = StrokeCap.Round))
        drawArc(Amber.copy(alpha = 0.20f), startAngle, sweepAngle, false, topLeft, arcSize,
            style = Stroke(width = strokePx + 6.dp.toPx(), cap = StrokeCap.Round))
    }
}
