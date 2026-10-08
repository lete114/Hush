package cn.imlete.apps.hush.timer

import org.junit.Assert.*
import org.junit.Test

class CountdownActionsTest {
    private val e = TimerEngine()
    private val now = 10_000L
    private val nowWall = 1_000_000L
    private val STEP = 5 * 60_000L
    private fun running30() =
        e.start(TimerState(choice = Choice.Preset(30 * 60_000L)), now, nowWall)
    private fun paused30() = e.pause(running30(), now + 60_000L)

    @Test fun `pause while running changes state cancels alarm and reanchors endAtWall`() {
        val o = CountdownActions.decide(running30(), CountdownEvent.Pause, STEP, now + 60_000L, nowWall + 60_000L)
        assertTrue(o.changed); assertTrue(o.state.paused)
        assertEquals(AlarmOp.CANCEL, o.alarm)
        assertFalse(o.shouldLock); assertFalse(o.stopService)
        // Mirrors HomeViewModel.pause: endAtWall = pause moment + frozen remaining (29 minutes)
        assertEquals(nowWall + 60_000L + 29 * 60_000L, o.state.endAtWall)
    }
    @Test fun `pause when idle is a no op`() {
        val idle = TimerState()
        val o = CountdownActions.decide(idle, CountdownEvent.Pause, STEP, now, nowWall)
        assertFalse(o.changed); assertEquals(AlarmOp.NONE, o.alarm)
    }
    @Test fun `pause when already paused is idempotent no op and does not reanchor endAtWall`() {
        val p = paused30()
        val o = CountdownActions.decide(p, CountdownEvent.Pause, STEP, now + 999_000L, nowWall + 999_000L)
        assertFalse(o.changed); assertEquals(AlarmOp.NONE, o.alarm)
        assertFalse(o.shouldLock); assertFalse(o.stopService)
        // engine.pause is idempotent for already paused state -> endAtWall is not moved again
        assertEquals(p, o.state)
    }
    @Test fun `resume while paused schedules alarm`() {
        val o = CountdownActions.decide(paused30(), CountdownEvent.Resume, STEP, now + 999_000L, nowWall + 999_000L)
        assertTrue(o.changed); assertTrue(o.state.running); assertFalse(o.state.paused)
        assertEquals(AlarmOp.SCHEDULE, o.alarm)
        assertFalse(o.stopService)
    }
    @Test fun `resume when idle does not schedule alarm`() {
        val o = CountdownActions.decide(TimerState(), CountdownEvent.Resume, STEP, now, nowWall)
        assertFalse(o.changed); assertEquals(AlarmOp.NONE, o.alarm)
        assertFalse(o.stopService)
    }
    @Test fun `extend while running increases step and reschedules`() {
        val o = CountdownActions.decide(running30(), CountdownEvent.Extend, STEP, now, nowWall)
        assertEquals(35 * 60_000L, o.state.totalMs)
        assertEquals(AlarmOp.SCHEDULE, o.alarm); assertTrue(o.changed)
        assertFalse(o.stopService)
    }
    @Test fun `extend while paused changes total but does not schedule alarm`() {
        val o = CountdownActions.decide(paused30(), CountdownEvent.Extend, STEP, now + 60_000L, nowWall + 60_000L)
        assertTrue(o.changed); assertEquals(AlarmOp.NONE, o.alarm)
        assertFalse(o.stopService)
    }
    @Test fun `extend when idle is no op`() {
        val o = CountdownActions.decide(TimerState(), CountdownEvent.Extend, STEP, now, nowWall)
        assertFalse(o.changed); assertEquals(AlarmOp.NONE, o.alarm)
        assertFalse(o.stopService)
    }
    @Test fun `cancel while running turns off stops service and cancels alarm`() {
        val o = CountdownActions.decide(running30(), CountdownEvent.Cancel, STEP, now, nowWall)
        assertFalse(o.state.running); assertEquals(Choice.Off, o.state.choice)
        assertTrue(o.stopService); assertEquals(AlarmOp.CANCEL, o.alarm); assertTrue(o.changed)
    }
    @Test fun `expire while running requests lock and cancels alarm`() {
        val o = CountdownActions.decide(running30(), CountdownEvent.Expire, STEP, now, nowWall)
        assertTrue(o.shouldLock); assertEquals(AlarmOp.CANCEL, o.alarm)
        assertFalse(o.changed); assertFalse(o.stopService)
    }
    @Test fun `expire while paused does not lock`() {
        val o = CountdownActions.decide(paused30(), CountdownEvent.Expire, STEP, now, nowWall)
        assertFalse(o.shouldLock); assertEquals(AlarmOp.CANCEL, o.alarm)
    }
    @Test fun `expire when idle does not lock`() {
        val o = CountdownActions.decide(TimerState(), CountdownEvent.Expire, STEP, now, nowWall)
        assertFalse(o.shouldLock)
    }

    // ---- cold start (new process = previous round ended): discard round, do not lock, cancel alarm (spec 2026-10-05-cold-start-discard-round) ----
    @Test fun `cold start while running discards round does not lock cancels alarm and preserves selected duration`() {
        val o = CountdownActions.decide(running30(), CountdownEvent.ColdStart, STEP, now, nowWall)
        assertTrue(o.changed)
        assertFalse(o.shouldLock); assertFalse(o.stopService)
        assertEquals(AlarmOp.CANCEL, o.alarm)
        assertFalse(o.state.running); assertFalse(o.state.paused)
        assertEquals(0L, o.state.endAt); assertEquals(0L, o.state.endAtWall); assertEquals(0L, o.state.totalMs)
        assertEquals(Choice.Preset(30 * 60_000L), o.state.choice)
    }
    @Test fun `cold start while paused discards round`() {
        val o = CountdownActions.decide(paused30(), CountdownEvent.ColdStart, STEP, now, nowWall)
        assertTrue(o.changed)
        assertFalse(o.shouldLock)
        assertEquals(AlarmOp.CANCEL, o.alarm)
        assertFalse(o.state.running); assertFalse(o.state.paused)
        assertEquals(Choice.Preset(30 * 60_000L), o.state.choice)
    }
    @Test fun `cold start when idle is no op and state unchanged`() {
        val idle = TimerState(choice = Choice.Preset(15 * 60_000L))
        val o = CountdownActions.decide(idle, CountdownEvent.ColdStart, STEP, now, nowWall)
        assertFalse(o.changed); assertFalse(o.shouldLock)
        assertEquals(AlarmOp.NONE, o.alarm)
        assertEquals(idle, o.state)
    }
    @Test fun `cold start never requests lock`() {
        for (s in listOf(running30(), paused30(), TimerState())) {
            assertFalse(CountdownActions.decide(s, CountdownEvent.ColdStart, STEP, now, nowWall).shouldLock)
        }
    }
}
