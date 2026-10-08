package cn.imlete.apps.hush.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug 2 regression: when paused, `pausedRemainingMs` (frozen remaining) written by persist
 * must be converted back to `TimerState.endAt` by [rebuildTimerState] so that remaining does
 * not drift after subsequent tick refreshes.
 */
class TimerRebuildTest {
    private val now = 10_000L
    private val nowWall = 1_000_000L

    @Test fun `rebuild paused sets endAt from frozen remaining and is invariant across now changes`() {
        val frozen = 29 * 60_000L
        val s = rebuildTimerState(
            now = now,
            nowWall = nowWall,
            endAtWall = nowWall + frozen,
            totalMs = 30 * 60_000L,
            running = false,
            paused = true,
            pausedRemainingMs = frozen,
            choice = Choice.Preset(30 * 60_000L),
            customDurationMs = 0,
        )
        assertEquals(frozen, s.endAt)
        assertTrue(s.paused); assertFalse(s.running)
        // simulate tick refreshes: remaining stays frozen under different now values
        assertEquals(frozen, TimerEngine().remainingMs(s, now + 200))
        assertEquals(frozen, TimerEngine().remainingMs(s, now + 600_000))
    }

    @Test fun `rebuild running sets endAt to monotonic clock deadline`() {
        val s = rebuildTimerState(
            now = now,
            nowWall = nowWall,
            endAtWall = nowWall + 30 * 60_000L,
            totalMs = 30 * 60_000L,
            running = true,
            paused = false,
            pausedRemainingMs = 0,
            choice = Choice.Preset(30 * 60_000L),
            customDurationMs = 0,
        )
        assertEquals(now + 30 * 60_000L, s.endAt)
        assertEquals(30 * 60_000L, TimerEngine().remainingMs(s, now))
    }

    @Test fun `persist to load restores paused frozen remaining across the full chain`() {
        // simulate HomeViewModel.persist: when paused, pausedRemainingMs = s.endAt (frozen remaining),
        // endAtWall reanchored to pause moment + frozen remaining.
        val engine = TimerEngine()
        val paused = engine.pause(
            engine.start(TimerState(choice = Choice.Preset(30 * 60_000L)), now, nowWall),
            now + 60_000L,
        )
        val refreshNow = now + 60_000L + 200   // one tick after refresh
        val refreshWall = nowWall + 60_000L + 200
        val rebuilt = rebuildTimerState(
            now = refreshNow,
            nowWall = refreshWall,
            endAtWall = refreshWall + paused.endAt,
            totalMs = paused.totalMs,
            running = paused.running,
            paused = paused.paused,
            pausedRemainingMs = paused.endAt,
            choice = paused.choice,
            customDurationMs = 0,
        )
        assertEquals(29 * 60_000L, rebuilt.endAt)
        assertEquals(29 * 60_000L, engine.remainingMs(rebuilt, refreshNow))
    }

    @Test fun `rebuild idle sets endAt to now and remaining to 0`() {
        val s = rebuildTimerState(
            now = now,
            nowWall = nowWall,
            endAtWall = 0,
            totalMs = 0,
            running = false,
            paused = false,
            pausedRemainingMs = 0,
            choice = Choice.Off,
            customDurationMs = 0,
        )
        assertEquals(now, s.endAt)
        assertEquals(0L, TimerEngine().remainingMs(s, now))
    }
}
