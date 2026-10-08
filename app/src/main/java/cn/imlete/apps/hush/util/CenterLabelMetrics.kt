package cn.imlete.apps.hush.util

/**
 * Center-label typography metrics. Long text drops to a smaller size and tighter tracking, so 8 characters (`HH:MM:SS`)
 * do not press into the arc within the 246dp ring's inner diameter (UI_SPEC §8.2-7).
 */
data class CenterLabelMetrics(
    /** Multiplier relative to the base size (56sp, `theme.DisplaySize`). */
    val scale: Float,
    /** Tracking, in sp. */
    val trackingSp: Float,
)

/**
 * ≤5 characters (`MM:SS`, longest like `60:00` / `65:00`) uses the base tier of 56sp + 2sp tracking;
 * ≥6 characters (`HH:MM:SS` at 8 chars, extra-long minute counts) drops to ×0.8 (≈44.8sp) with 0 tracking,
 * 8 characters ≈197dp < 246dp inner diameter, leaving ≈49dp margin.
 */
fun centerLabelMetrics(text: String): CenterLabelMetrics =
    if (text.length <= 5) CenterLabelMetrics(scale = 1f, trackingSp = 2f)
    else CenterLabelMetrics(scale = 0.8f, trackingSp = 0f)
