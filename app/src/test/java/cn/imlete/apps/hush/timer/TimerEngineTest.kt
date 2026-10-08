package cn.imlete.apps.hush.timer

import cn.imlete.apps.hush.util.DurationFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {
    private val e = TimerEngine()
    private val now = 10_000L
    private val nowWall = 1_000_000L

    private fun startPreset(min: Long = 30) =
        e.start(TimerState(choice = Choice.Preset(min * 60_000L)), now, nowWall)

    @Test fun `start sets endAt and running`() {
        val s = startPreset()
        assertEquals(now + 30 * 60_000L, s.endAt)
        assertTrue(s.running); assertFalse(s.paused)
        assertEquals(30 * 60_000L, s.totalMs)
    }
    @Test fun `pause freezes remaining`() {
        val s1 = e.pause(startPreset(), now + 60_000L)
        assertEquals(29 * 60_000L, e.remainingMs(s1, now + 999_000L))
        assertTrue(s1.paused); assertFalse(s1.running)
    }
    @Test fun `resume resets endAt based on frozen remaining`() {
        val s1 = e.pause(startPreset(), now + 60_000L)
        val s2 = e.resume(s1, now + 999_000L, nowWall + 999_000L)
        assertEquals(29 * 60_000L, e.remainingMs(s2, now + 999_000L))
        assertFalse(s2.paused); assertTrue(s2.running)
    }
    @Test fun `extend clamps total and endAt together`() {
        val s0 = startPreset()
        val s1 = e.extend(s0, now, ServiceTimerMath.STEP_MS)
        assertEquals(35 * 60_000L, s1.totalMs)
        assertEquals(s0.endAt + 5 * 60_000L, s1.endAt)
    }
    @Test fun `reduce does not go below one step`() {
        val s1 = e.reduce(startPreset(), now, ServiceTimerMath.STEP_MS)
        assertEquals(25 * 60_000L, s1.totalMs)
    }
    @Test fun `reduce at exactly one step does not change`() {
        val s0 = e.start(TimerState(choice = Choice.Preset(5 * 60_000L)), now, nowWall)
        assertEquals(5 * 60_000L, e.reduce(s0, now, ServiceTimerMath.STEP_MS).totalMs)
    }
    @Test fun `off stops and preserves customDurationMs`() {
        val s0 = TimerState(choice = Choice.Custom(12 * 60_000L), customDurationMs = 12 * 60_000L, running = true)
        val s1 = e.off(s0)
        assertFalse(s1.running); assertEquals(Choice.Off, s1.choice); assertEquals(12 * 60_000L, s1.customDurationMs)
    }
    @Test fun `roundOver ends round but preserves selected duration`() {
        val s0 = e.start(
            TimerState(choice = Choice.Custom(45 * 60_000L), customDurationMs = 45 * 60_000L),
            now, nowWall,
        )
        val s1 = e.roundOver(s0)
        assertFalse(s1.running); assertFalse(s1.paused)
        assertEquals(0L, s1.endAt); assertEquals(0L, s1.endAtWall); assertEquals(0L, s1.totalMs)
        assertEquals(Choice.Custom(45 * 60_000L), s1.choice)
        assertEquals(45 * 60_000L, s1.customDurationMs)
    }
    @Test fun `roundOver when paused also clears state`() {
        val paused = e.pause(startPreset(), now + 60_000L)
        val s1 = e.roundOver(paused)
        assertFalse(s1.running); assertFalse(s1.paused)
        assertEquals(0L, s1.endAt); assertEquals(Choice.Preset(30 * 60_000L), s1.choice)
    }
    @Test fun `select while running replaces round with new duration`() {
        val s1 = e.select(startPreset(), Choice.Preset(15 * 60_000L), now, nowWall)
        assertEquals(15 * 60_000L, s1.totalMs); assertTrue(s1.running)
        assertEquals(now + 15 * 60_000L, s1.endAt)
    }
    @Test fun `select while not running only changes choice`() {
        val s1 = e.select(TimerState(), Choice.Preset(15 * 60_000L), now, nowWall)
        assertEquals(Choice.Preset(15 * 60_000L), s1.choice); assertFalse(s1.running)
    }
    @Test fun `select while paused replaces frozen remaining with new duration`() {
        val pausedAt = now + 60_000L
        val paused = e.pause(startPreset(), pausedAt) // frozen 29 minutes at pause time
        val s1 = e.select(paused, Choice.Preset(15 * 60_000L), pausedAt, nowWall + 60_000L)
        assertTrue(s1.paused); assertFalse(s1.running)
        assertEquals(Choice.Preset(15 * 60_000L), s1.choice)
        assertEquals(15 * 60_000L, s1.totalMs)
        // frozen remaining replaced with new duration; no drift from monotonic clock after that
        assertEquals(15 * 60_000L, e.remainingMs(s1, pausedAt + 999_000L))
        // wall-clock endAt anchored to "pause moment + new duration", matching HomeViewModel.pause anchoring
        assertEquals(nowWall + 60_000L + 15 * 60_000L, s1.endAtWall)
    }
    @Test fun `remainingMs is not negative`() {
        assertEquals(0L, e.remainingMs(startPreset(), now + 99 * 60_000L))
    }
    @Test fun `progress calculation`() {
        assertEquals(0.5f, e.progress(startPreset(), now + 15 * 60_000L), 0.0001f)
    }
    @Test fun `progress last second reaches zero at same frame as label 00 00`() {
        // remaining 500ms < 1s: formatCountdown already shows 00:00; progress arc must also reach zero at the same frame
        // (spec 2026-10-07-ring-zero-alignment §1)
        assertEquals(0f, e.progress(startPreset(), now + 30 * 60_000L - 500L), 0f)
    }
    @Test fun `progress pre close landing at exactly 1 second is zero`() {
        // remaining 1000ms: pre-close window lands at g=0 -> 0f, at most 1 frame before label flips to 00:00 (r <= 999);
        // combined with existing 200ms interpolation lag appears visually at the same frame (spec 2026-10-07-ring-pre-close-ramp §3/§4;
        // replaces old assertion "exactly 1 second remaining does not zero early" which was based on the pure real-ratio curve from 785f910)
        assertEquals(0f, e.progress(startPreset(), now + 30 * 60_000L - 1000L), 0f)
    }
    @Test fun `progress pre close window top at 3 seconds matches real ratio seamlessly`() {
        // remaining 3000ms = top of pre-close window (x=1 -> g=1): equals real ratio bitwise with no seam at junction
        val expected = 3_000f / (30 * 60_000L).toFloat()
        assertEquals(expected, e.progress(startPreset(), now + 30 * 60_000L - 3_000L), 1e-6f)
    }
    @Test fun `progress pre close window midpoint at 2 seconds is 75 percent of real ratio`() {
        // remaining 2000ms -> x=0.5 -> g = 1 − (1 − 0.5)^2 = 0.75 (formula in spec §2 paragraph 2)
        val expected = 2_000f / (30 * 60_000L).toFloat() * 0.75f
        assertEquals(expected, e.progress(startPreset(), now + 30 * 60_000L - 2_000L), 1e-6f)
    }
    @Test fun `progress pre close window is monotonic decreasing and not above real ratio`() {
        val total = 30 * 60_000L
        val rs = longArrayOf(3_000L, 2_600L, 2_200L, 1_800L, 1_400L, 1_000L)
        var prev = Float.POSITIVE_INFINITY
        for (r in rs) {
            val v = e.progress(startPreset(), now + total - r)
            assertTrue("r=$r should not exceed real ratio", v <= r.toFloat() / total + 1e-6f)
            assertTrue("r=$r should be strictly less than previous frame", v < prev)
            prev = v
        }
    }
    @Test fun `progress when total duration is 0 is always 0`() {
        // fallback when totalMs <= 0: returns 0f for any remaining (spec §6 regression)
        val s = TimerState(running = true, endAt = now + 60_000L)
        assertEquals(0f, e.progress(s, now), 0f)
    }
    @Test fun `canReduce is strictly greater than step`() {
        val s0 = startPreset()
        assertFalse(e.canReduce(s0, now + 25 * 60_000L, ServiceTimerMath.STEP_MS))
        assertTrue(e.canReduce(s0, now + 24 * 60_000L, ServiceTimerMath.STEP_MS))
    }
    @Test fun `canReduce is true while paused`() {
        val pausedAt = now + 60_000L
        val paused = e.pause(startPreset(), pausedAt) // frozen 29 minutes at pause time
        assertTrue(e.canReduce(paused, pausedAt, ServiceTimerMath.STEP_MS))
    }
    @Test fun `start invariant endAt minus now equals total`() {
        val s0 = startPreset()
        assertEquals(s0.totalMs, s0.endAt - now)
    }
    @Test fun `extend remaining does not exceed total`() {
        val s1 = e.extend(startPreset(), now, ServiceTimerMath.STEP_MS)
        assertTrue(e.remainingMs(s1, now) <= s1.totalMs)
    }
    @Test fun `format integration shows full HHMMSS while running`() {
        assertEquals("00:15:00", DurationFormatter.format(e.remainingMs(startPreset(), now + 15 * 60_000L)))
    }

    @Test fun `pause freezes endAtWall`() {
        val s1 = e.pause(startPreset(), now + 10 * 60_000L)
        assertEquals(nowWall + 30 * 60_000L, s1.endAtWall)
    }
    @Test fun `paused extend advances endAtWall and preserves invariant`() {
        val pauseWall = nowWall + 10 * 60_000L
        val s1 = e.pause(startPreset(), now + 10 * 60_000L)
        val s2 = e.extend(s1, now + 10 * 60_000L, ServiceTimerMath.STEP_MS)
        assertEquals(25 * 60_000L, e.remainingMs(s2, now + 10 * 60_000L))
        assertEquals(s2.endAtWall - pauseWall, e.remainingMs(s2, now + 10 * 60_000L))
    }
    @Test fun `paused reduce moves endAtWall back`() {
        val s1 = e.pause(startPreset(), now + 10 * 60_000L)
        val s2 = e.reduce(s1, now + 10 * 60_000L, ServiceTimerMath.STEP_MS)
        assertEquals(15 * 60_000L, e.remainingMs(s2, now + 10 * 60_000L))
        assertEquals(nowWall + 25 * 60_000L, s2.endAtWall)
    }
    @Test fun `select preset preserves saved custom duration`() {
        val custom = TimerState(
            choice = Choice.Custom(12 * 60_000L), customDurationMs = 12 * 60_000L,
            running = true, endAt = now + 12 * 60_000L, totalMs = 12 * 60_000L,
        )
        val s1 = e.select(custom, Choice.Preset(15 * 60_000L), now, nowWall)
        assertEquals(12 * 60_000L, s1.customDurationMs)
        assertEquals(15 * 60_000L, s1.totalMs)
    }
    @Test fun `idle extend is no op`() =
        assertEquals(TimerState(), e.extend(TimerState(), now, ServiceTimerMath.STEP_MS))
    @Test fun `idle resume is no op`() = assertEquals(TimerState(), e.resume(TimerState(), now, nowWall))
    @Test fun `pause is idempotent`() {
        val s1 = e.pause(startPreset(), now + 60_000L)
        assertEquals(s1, e.pause(s1, now + 999_000L))
    }
    @Test fun `extend at limit is no op`() {
        val s0 = e.start(TimerState(choice = Choice.Custom(ServiceTimerMath.MAX_DURATION_MS)), now, nowWall)
        assertEquals(s0, e.extend(s0, now, ServiceTimerMath.STEP_MS))
    }

    @Test fun `extend with custom step 1 minute`() {
        val s1 = e.extend(startPreset(), now, 60_000L)
        assertEquals(31 * 60_000L, s1.totalMs)
    }
    @Test fun `reduce with custom step 30 minutes`() {
        // step >= remaining -> no change: reduce(30min, 30min) = max(0, min(30, 0)) = 0 -> totalMs unchanged
        val s1 = e.reduce(startPreset(), now, 1_800_000L)
        assertEquals(30 * 60_000L, s1.totalMs)
    }
    @Test fun `canReduce with custom step lower bound`() {
        val s0 = startPreset()  // 30 minutes
        assertFalse(e.canReduce(s0, now + 29 * 60_000L, 60_000L)) // remaining 1 minute <= step 1 minute
        assertTrue(e.canReduce(s0, now + 28 * 60_000L, 60_000L))  // remaining 2 minutes > 1 minute
    }

    // ---- criterion for having a valid duration (spec 2026-10-05-idle-start-opens-drawer) ----
    @Test fun `hasChosenDuration Off returns false`() {
        assertFalse(hasChosenDuration(TimerState(choice = Choice.Off)))
    }
    @Test fun `hasChosenDuration Preset 15 minutes returns true`() {
        assertTrue(hasChosenDuration(TimerState(choice = Choice.Preset(15 * 60_000L))))
    }
    @Test fun `hasChosenDuration Custom 0 returns false`() {
        assertFalse(hasChosenDuration(TimerState(choice = Choice.Custom(0))))
    }
    @Test fun `hasChosenDuration Custom 1 second returns true`() {
        assertTrue(hasChosenDuration(TimerState(choice = Choice.Custom(1000L))))
    }

    // ---- start fallback: no valid duration does not start (spec 2026-10-05-idle-start-opens-drawer §5.3) ----
    @Test fun `start with Off returns unchanged and does not silently start as 30 minutes`() {
        val s0 = TimerState(choice = Choice.Off)
        val s1 = e.start(s0, now, nowWall)
        assertEquals(s0, s1)
        assertFalse(s1.running)
        assertEquals(0L, s1.endAt); assertEquals(0L, s1.totalMs)
    }
    @Test fun `start with Custom 0 returns unchanged to avoid immediate expiration`() {
        val s0 = TimerState(choice = Choice.Custom(0), customDurationMs = 0L)
        assertEquals(s0, e.start(s0, now, nowWall))
    }
}