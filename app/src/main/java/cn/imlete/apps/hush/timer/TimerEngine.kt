package cn.imlete.apps.hush.timer

data class TimerState(
    /**
     * While running: the monotonic-clock deadline (in the `SystemClock.elapsedRealtime()`
     * domain); here `remaining = endAt − now`.
     * While paused: stores the **frozen remaining millis**; here `remaining = endAt`.
     * This is exactly why [TimerEngine.remainingMs] must branch on `paused`.
     */
    val endAt: Long = 0,
    val endAtWall: Long = 0,
    val totalMs: Long = 0,
    val running: Boolean = false,
    val paused: Boolean = false,
    val choice: Choice = Choice.Off,
    val customDurationMs: Long? = null,
)

sealed class Choice {
    object Off : Choice()
    data class Preset(val durationMs: Long) : Choice()
    data class Custom(val durationMs: Long) : Choice()
}

/**
 * Whether a **valid** duration has been selected (so a countdown can start). Both `Off` and a
 * zero duration count as "not selected".
 *
 * The difference from [cn.imlete.apps.hush.data.selectedDurationOf]: that one returns a
 * 30-minute placeholder for [Choice.Off] (for the "what time will it end" estimate and
 * persistence), while `Off` here means "no duration selected" and **cannot start** — mixing
 * the two up is exactly the root cause of "tapping start at 00:00 becomes 30:00"
 * (spec 2026-10-05-idle-start-opens-drawer).
 */
fun hasChosenDuration(s: TimerState): Boolean = when (val c = s.choice) {
    Choice.Off -> false
    is Choice.Preset -> c.durationMs > 0
    is Choice.Custom -> c.durationMs > 0
}

/** Label rounding quantum: remaining &lt; 1s shows `00:00` (changes in lockstep with `DurationFormatter.formatCountdown`). */
private const val LABEL_ZERO_MS = 1_000L
/** Length of the final-ramp pre-close window (spec 2026-10-07-ring-pre-close-ramp §2): the junction point = label quantum + window length. */
private const val PRE_CLOSE_WINDOW_MS = 2_000L
/** Top of the pre-close window: `r > this value` uses the true ratio; `[LABEL_ZERO_MS, this value]` uses the pre-close curve. */
private const val PRE_CLOSE_END_MS = LABEL_ZERO_MS + PRE_CLOSE_WINDOW_MS

class TimerEngine {
    fun start(state: TimerState, now: Long, nowWall: Long): TimerState {
        // Do not start without a valid duration (Off / zero duration): the UI already
        // reinterprets such a tap as "open the duration drawer"; this is the pure-layer
        // fallback — never silently start with durationOf's 30-minute placeholder
        // (spec 2026-10-05-idle-start-opens-drawer).
        if (!hasChosenDuration(state)) return state
        val total = durationOf(state.choice)
        return state.copy(
            endAt = now + total,
            endAtWall = nowWall + total,
            totalMs = total,
            running = true,
            paused = false,
        )
    }

    fun pause(state: TimerState, now: Long): TimerState {
        if (!state.running || state.paused) return state
        return state.copy(running = false, paused = true, endAt = remainingMs(state, now))
    }

    fun resume(state: TimerState, now: Long, nowWall: Long): TimerState {
        if (!state.paused) return state
        val r = remainingMs(state, now)
        if (r <= 0) return state
        return state.copy(endAt = now + r, endAtWall = nowWall + r, running = true, paused = false)
    }

    fun extend(state: TimerState, now: Long, stepMs: Long): TimerState {
        if (!state.running && !state.paused) return state
        val remaining = remainingMs(state, now)
        val (newTotal, newRemaining) = ServiceTimerMath.extend(state.totalMs, remaining, stepMs)
        val applied = newRemaining - remaining
        return if (applied <= 0) state else state.copy(
            totalMs = newTotal,
            endAt = state.endAt + applied,
            endAtWall = state.endAtWall + applied,
        )
    }

    fun reduce(state: TimerState, now: Long, stepMs: Long): TimerState {
        if (!state.running && !state.paused) return state
        val remaining = remainingMs(state, now)
        val applied = ServiceTimerMath.reduce(remaining, stepMs)
        return if (applied <= 0) state else state.copy(
            totalMs = state.totalMs - applied,
            endAt = state.endAt - applied,
            endAtWall = state.endAtWall - applied,
        )
    }

    fun off(state: TimerState): TimerState =
        state.copy(running = false, paused = false, choice = Choice.Off)

    /**
     * End the current round but **keep the selected duration** — shared by the
     * end-of-countdown cleanup and cold-start voiding.
     * The difference from [off]: `off` is the user actively "cancelling" and clears `choice`
     * too; here only the round fields are cleared.
     */
    fun roundOver(state: TimerState): TimerState =
        state.copy(endAt = 0, endAtWall = 0, totalMs = 0, running = false, paused = false)

    fun select(state: TimerState, choice: Choice, now: Long, nowWall: Long): TimerState {
        if (choice == Choice.Off) return off(state)
        val savedCustom = (choice as? Choice.Custom)?.durationMs ?: state.customDurationMs
        val next = state.copy(choice = choice, customDurationMs = savedCustom)
        val total = durationOf(choice)
        return when {
            // While paused, endAt holds the frozen remaining: swapping to a new duration
            // re-anchors the wall-clock deadline to "now + new duration", consistent with
            // HomeViewModel.pause's anchoring (otherwise the "ends at what time" label would
            // point at the previous round).
            state.paused -> next.copy(endAt = total, endAtWall = nowWall + total, totalMs = total)
            state.running -> next.copy(endAt = now + total, endAtWall = nowWall + total, totalMs = total, paused = false)
            else -> next
        }
    }

    /**
     * Remaining millis. While running, [TimerState.endAt] is the monotonic-clock deadline, so
     * `remaining = endAt − now`; while paused, [TimerState.endAt] holds the frozen remaining,
     * so `remaining = endAt`.
     */
    fun remainingMs(state: TimerState, now: Long): Long =
        if (state.paused) state.endAt.coerceAtLeast(0)
        else (state.endAt - now).coerceAtLeast(0)

    /**
     * Progress ratio 0f..1f. Three-segment curve (spec 2026-10-07-ring-pre-close-ramp §2):
     * - `r > PRE_CLOSE_END_MS`: true remaining ratio (unchanged for almost the entire duration);
     * - `r ∈ [1000, 3000]` (final 3s pre-close window): true ratio × smoothstep
     *   `g = 1 − (1 − x)²` (`x = (r − LABEL_ZERO_MS) / PRE_CLOSE_WINDOW_MS`). The junction
     *   `g' = 0` is seamless with the natural speed, the mid-window peak is always ≤1.75× the
     *   natural speed, and the final tick's step is ≤1.14× a normal tick (independent of round
     *   length) — spreading the old "hold until 00:00, then jump through one 200ms tween plus a
     *   boolean-gate hard cut" visual jump into a smooth finishing rush;
     * - `r < LABEL_ZERO_MS`: always `0f`, under the same condition as
     *   `DurationFormatter.formatCountdown` showing `00:00` (rounding quantum 1s) — it lands at
     *   most 1 tick early and visually matches the label on the same tick
     *   (spec 2026-10-07-ring-zero-alignment §1).
     * The final-ramp value **no longer equals the true remaining ratio** (it is ahead of the
     * real progress; by design). The only consumer of `progress` is the home-screen dashboard
     * ring (spec 785f910 fact 4). If the rounding quantum / pre-close window changes, this
     * must be kept in sync here. The lowest available step (custom ≥60s) is far above the 3s
     * window top; introducing steps shorter than 3s would need re-review.
     */
    fun progress(state: TimerState, now: Long): Float {
        val total = state.totalMs
        if (total <= 0) return 0f
        val r = remainingMs(state, now)
        if (r < LABEL_ZERO_MS) return 0f
        if (r > PRE_CLOSE_END_MS) return r.toFloat() / total
        val x = (r - LABEL_ZERO_MS).toFloat() / PRE_CLOSE_WINDOW_MS
        val g = 1f - (1f - x) * (1f - x)
        return (r.toFloat() / total) * g
    }

    fun canReduce(state: TimerState, now: Long, stepMs: Long): Boolean =
        (state.running || state.paused) && remainingMs(state, now) > stepMs

    private fun durationOf(choice: Choice): Long = when (choice) {
        is Choice.Preset -> choice.durationMs
        is Choice.Custom -> choice.durationMs
        // The UI never reaches here from Off (start's first line is already blocked by
        // hasChosenDuration); this is only a placeholder fallback.
        // The 30-minute placeholder for persistence/estimation lives in data.selectedDurationOf;
        // don't "fix" it here as a side improvement.
        Choice.Off -> 30 * 60_000L
    }
}

/**
 * Rebuild [TimerState] from the persisted fields (pure function, unit-testable on the JVM).
 * While paused, [TimerState.endAt] takes [pausedRemainingMs] (frozen remaining); while
 * running it takes `now + remaining`.
 * [HomeViewModel.loadFromStore] delegates to this function, guaranteeing that the paused
 * remaining does not drift across per-tick refreshes.
 */
fun rebuildTimerState(
    now: Long,
    nowWall: Long,
    endAtWall: Long,
    totalMs: Long,
    running: Boolean,
    paused: Boolean,
    pausedRemainingMs: Long,
    choice: Choice,
    customDurationMs: Long,
): TimerState {
    val remaining = if (running || paused) (endAtWall - nowWall).coerceAtLeast(0) else 0L
    return TimerState(
        // While paused, endAt stores the frozen remaining (explicitly persisted); while running,
        // endAt is the monotonic-clock deadline.
        endAt = if (paused) pausedRemainingMs else now + remaining,
        endAtWall = endAtWall,
        totalMs = totalMs,
        running = running,
        paused = paused,
        choice = choice,
        customDurationMs = customDurationMs.takeIf { it > 0 },
    )
}
