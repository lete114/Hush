package cn.imlete.apps.hush

import cn.imlete.apps.hush.timer.TimerEngine
import cn.imlete.apps.hush.timer.TimerState
import cn.imlete.apps.hush.util.DurationFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rounding quantum contract (spec 2026-10-07-ring-zero-alignment §1 / ring-pre-close-ramp §2 §4):
 * the center label showing `00:00` and the progress arc reaching zero must be pinned to the
 * **same 1s quantum** — on the label side `DurationFormatter` uses `maxOf(0, ms) / 1000`, and on
 * the arc side `TimerEngine.progress` uses `LABEL_ZERO_MS = 1000`.
 * If either side changes its quantum without the other following, this test turns red immediately
 * (mechanical replacement for the M1/M3 follow-up; `DurationFormatter` is frozen by spec §5,
 * so no production code is changed — only this guard is added).
 */
class ZeroQuantumContractTest {
    private val engine = TimerEngine()
    private val now = 10_000L

    /** State with [remainingMs] left in a preset 30-minute round (MM:SS label branch). */
    private fun stateAt(remainingMs: Long) = TimerState(
        running = true,
        endAt = now + remainingMs,
        totalMs = 30 * 60_000L,
    )

    // ---- label side: floor(ms / 1000) < 1 ⟺ 00:00 shown ----
    @Test fun `label 999ms shows 00 00`() {
        assertEquals("00:00", DurationFormatter.formatCountdown(false, 30 * 60_000L, 999L))
    }
    @Test fun `label 1000ms shows 00 01`() {
        assertEquals("00:01", DurationFormatter.formatCountdown(false, 30 * 60_000L, 1000L))
    }

    // ---- arc side: r <= 1000 is zero; from r = 1001 the pre-close window begins (> 0) ----
    @Test fun `progress 999ms is zero`() = assertEquals(0f, engine.progress(stateAt(999L), now), 0f)
    @Test fun `progress 1000ms is zero`() = assertEquals(0f, engine.progress(stateAt(1000L), now), 0f)
    @Test fun `progress 1001ms is above zero`() {
        assertTrue("r=1001 should be > 0 (pre-close window start x=0.0005, g ~= 0.001)", engine.progress(stateAt(1001L), now) > 0f)
    }
}