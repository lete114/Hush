package cn.imlete.apps.hush.timer

object ServiceTimerMath {
    /** Default step of 5 minutes (same value as `SettingsStore.DEFAULT_STEP_MINUTES`); the actual step is passed in by the caller as `stepMs`. */
    const val STEP_MS = 5 * 60_000L
    const val MAX_DURATION_MS = (23 * 3600 + 59 * 60) * 1000L

    /**
     * Pure function: a helper that computes the remaining millis from `(total, base, now)`,
     * for use by the future `TimerService` (`base` is the base instant at which
     * `remaining == total`). [TimerEngine] uses the equivalent `endAt − now` form.
     */
    fun remainingAt(totalMs: Long, base: Long, now: Long): Long =
        (totalMs - (now - base)).coerceAtLeast(0)

    fun extend(totalMs: Long, remainingMs: Long, stepMs: Long): Pair<Long, Long> {
        val applied = minOf(stepMs, MAX_DURATION_MS - totalMs)
        return (totalMs + applied) to (remainingMs + applied)
    }

    fun reduce(remainingMs: Long, stepMs: Long): Long =
        maxOf(0L, minOf(stepMs, remainingMs - stepMs))
}
