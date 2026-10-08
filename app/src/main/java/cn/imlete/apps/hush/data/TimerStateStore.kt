package cn.imlete.apps.hush.data

import cn.imlete.apps.hush.timer.Choice
import cn.imlete.apps.hush.timer.TimerState
import cn.imlete.apps.hush.timer.rebuildTimerState

/** Placeholder duration when no duration is selected (consistent with TimerEngine.durationOf's Off fallback). */
const val DEFAULT_DURATION_MS = 30 * 60_000L

/** Total duration of this round: takes the currently selected duration; falls back to the placeholder on Off (used only for the "ends at" estimate and persistence). */
fun selectedDurationOf(s: TimerState): Long = when (val c = s.choice) {
    is Choice.Preset -> c.durationMs
    is Choice.Custom -> c.durationMs
    Choice.Off -> DEFAULT_DURATION_MS
}

/** Timer state → persisted fields (shared by HomeViewModel and CountdownService; each side copying its own is forbidden). */
fun SettingsStore.saveState(s: TimerState) {
    endAtWall = s.endAtWall
    totalMs = s.totalMs
    running = s.running
    paused = s.paused
    pausedRemainingMs = if (s.paused) s.endAt else 0L
    choiceType = when (s.choice) {
        Choice.Off -> CHOICE_OFF
        is Choice.Preset -> CHOICE_PRESET
        is Choice.Custom -> CHOICE_CUSTOM
    }
    choiceDurationMs = selectedDurationOf(s)
    customDurationMs = s.customDurationMs ?: 0L
}

/** Persisted fields → timer state (delegates to the pure function rebuildTimerState; rules see TimerRebuildTest). */
fun SettingsStore.loadState(now: Long, nowWall: Long): TimerState = rebuildTimerState(
    now = now,
    nowWall = nowWall,
    endAtWall = endAtWall,
    totalMs = totalMs,
    running = running,
    paused = paused,
    pausedRemainingMs = pausedRemainingMs,
    choice = when (choiceType) {
        CHOICE_PRESET -> Choice.Preset(choiceDurationMs.takeIf { it > 0 } ?: DEFAULT_DURATION_MS)
        CHOICE_CUSTOM -> Choice.Custom(choiceDurationMs.takeIf { it > 0 } ?: DEFAULT_DURATION_MS)
        else -> Choice.Off
    },
    customDurationMs = customDurationMs,
)

private const val CHOICE_OFF = "off"
private const val CHOICE_PRESET = "preset"
private const val CHOICE_CUSTOM = "custom"
