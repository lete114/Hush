package cn.imlete.apps.hush.util

import java.util.TimeZone

object DurationFormatter {
    const val MAX_DURATION_MS = (23 * 3600 + 59 * 60) * 1000L

    fun format(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        return "${pad(s / 3600)}:${pad((s % 3600) / 60)}:${pad(s % 60)}"
    }

    fun formatMinutes(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        return "${pad(s / 60)}:${pad(s % 60)}"
    }

    /** Semantics already covered by [formatMinutes]; kept for compatibility with existing calls and assertions. */
    fun formatPreset(ms: Long): String = formatMinutes(ms)

    /**
     * Main center-label format: HH:MM:SS only when "custom and total > 60 minutes"; everything else (all presets,
     * ≤60-minute custom, Off falling back to 30 minutes) uses MM:SS. The format is decided by choice type + total duration,
     * independent of the remaining time — the character count of a whole countdown never changes (design §3.1).
     */
    fun formatCountdown(isCustom: Boolean, totalMs: Long, valueMs: Long): String =
        if (isCustom && maxOf(0L, totalMs) > 60 * 60_000L) format(valueMs) else formatMinutes(valueMs)

    fun formatCompact(ms: Long): String {
        val s = maxOf(0L, ms) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "${pad(h)}:${pad(m)}:${pad(sec)}" else "${pad(m)}:${pad(sec)}"
    }

    /**
     * Wall-clock HH:mm (24-hour): `endAtWall` lives in the `System.currentTimeMillis()` domain and must be converted per the **device's local time zone**
     * before taking the modulo — taking the modulo directly on the epoch yields UTC, shifting the whole thing by the local UTC offset (e.g. UTC+8 displays 8 hours late).
     * Add the default time zone's offset at that instant first, then reuse the existing day modulo (the midnight carry-over still holds on the local value).
     * JDK-only (minSdk 24 has no desugaring, so `java.time` is unavailable).
     */
    fun formatClock(epochMs: Long): String {
        val localMs = epochMs + TimeZone.getDefault().getOffset(epochMs)
        val totalMin = Math.floorDiv(localMs, 60000L)
        val dayMin = ((totalMin % 1440) + 1440) % 1440
        return "${pad(dayMin / 60)}:${pad(dayMin % 60)}"
    }

    private fun pad(v: Long): String = if (v < 10) "0$v" else v.toString()
}
