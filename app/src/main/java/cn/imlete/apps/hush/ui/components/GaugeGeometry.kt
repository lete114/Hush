package cn.imlete.apps.hush.ui.components

internal const val GaugeSweepDeg = 270f
internal const val GaugeStartDeg = 135f
internal const val GaugeTickCount = 11

internal fun gaugeProgressSweepDeg(progress: Float): Float {
    if (progress.isNaN()) return 0f
    return GaugeSweepDeg * progress.coerceIn(0f, 1f)
}

/**
 * Tick angle list.
 *
 * The returned angles are **deliberately not normalized**: the range is 135f..405f (rather than 135f..45f).
 * Since sin/cos are periodic, the rendering side can use them directly without normalization.
 */
internal fun gaugeTickAnglesDeg(): List<Float> =
    List(GaugeTickCount) { i -> GaugeStartDeg + GaugeSweepDeg * i / (GaugeTickCount - 1) }

/** Cached tick angles used at render time, avoiding reallocating the list every frame. */
internal val GaugeTickAngles: List<Float> = gaugeTickAnglesDeg()

/**
 * Computes the tick line's radius interval (outer, inner), in the same unit as the arguments (px).
 * outer = arc radius − half stroke width − gap; inner = outer − tick length, and never less than 0.
 */
internal fun gaugeTickRadiiPx(
    radiusPx: Float,
    strokePx: Float,
    gapPx: Float,
    lengthPx: Float,
): Pair<Float, Float> {
    val outer = radiusPx - strokePx / 2f - gapPx
    val inner = (outer - lengthPx).coerceAtLeast(0f)
    return outer to inner
}
