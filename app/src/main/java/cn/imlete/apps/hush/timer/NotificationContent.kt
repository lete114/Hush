package cn.imlete.apps.hush.timer

/**
 * Notification content snapshot: normalizes [TimerState] into the minimal data needed by
 * notification rendering.
 *
 * Responsibility boundary: this class only produces **numbers and switches**; Chinese templates
 * such as "Hush countdown", "Paused", "+N minutes" are always assembled by the Android glue
 * layer from `strings.xml` (the pure logic layer has no access to `Context`/resources).
 *
 * 2026-10-04 UX spec §3: the running-state body no longer carries the end time (the end time
 * is only shown in the home-screen subtitle); the system Chronometer counts down second by
 * second using [endAtWall] as its base. [showWhen] is only true in the running state —
 * the paused state hides the when timestamp, avoiding a fake clock that shows "Now" on AOSP /
 * the frozen time on MIUI.
 *
 * The paused-state body carries [pausedRemainingMs] (frozen remaining; the value does not
 * change while paused, so it can be rendered statically without a per-second refresh problem);
 * [totalMs] + [custom] are passed through to the glue layer to pick the
 * `DurationFormatter.formatCountdown` format — the same format rules as the home-screen center
 * label, so the two places never show inconsistent time formats.
 */
data class NotificationContent(
    /** An active round exists (running or paused) → the notification exists. */
    val active: Boolean,
    /** Currently counting (running and not paused) → the system Chronometer renders the countdown. */
    val counting: Boolean,
    /** End wall-clock time; passed through only in the active state (running/paused), 0 when idle. */
    val endAtWall: Long,
    /** Only true in the running state: shows the when timestamp (hidden in the paused state, see the class comment). */
    val showWhen: Boolean,
    /** Step size in minutes for the notification's "+N minutes" button. */
    val extendMinutes: Int,
    /** Frozen remaining millis in the paused state; always 0 otherwise (the running-state countdown is rendered by the system Chronometer, not through the body text). */
    val pausedRemainingMs: Long,
    /** Total duration of the current round (the totalMs parameter of formatCountdown). */
    val totalMs: Long,
    /** Whether it is a custom duration (the isCustom parameter of formatCountdown). */
    val custom: Boolean,
) {
    companion object {
        fun build(state: TimerState, stepMinutes: Int): NotificationContent {
            val active = state.running || state.paused
            val counting = state.running && !state.paused
            return NotificationContent(
                active = active,
                counting = counting,
                endAtWall = if (active) state.endAtWall else 0L,
                showWhen = counting,
                extendMinutes = stepMinutes,
                pausedRemainingMs = if (state.paused) state.endAt.coerceAtLeast(0L) else 0L,
                totalMs = state.totalMs,
                custom = state.choice is Choice.Custom,
            )
        }
    }
}
