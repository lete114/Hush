package cn.imlete.apps.hush.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {
    private val engine = TimerEngine()
    private val now = 10_000L
    private val nowWall = 1_000_000L
    private val END_WALL = nowWall + 30 * 60_000L

    private fun running30() =
        engine.start(TimerState(choice = Choice.Preset(30 * 60_000L)), now, nowWall)

    @Test fun `running is active and counting endAtWall passed through showWhen true`() {
        val c = NotificationContent.build(running30(), stepMinutes = 5)
        assertTrue(c.active); assertTrue(c.counting)
        assertEquals(END_WALL, c.endAtWall)
        assertTrue(c.showWhen)
        assertEquals(5, c.extendMinutes)
    }

    @Test fun `paused is active but not counting showWhen false`() {
        val paused = engine.pause(running30(), now + 60_000L)
        val c = NotificationContent.build(paused, 5)
        assertTrue(c.active); assertFalse(c.counting)
        assertFalse(c.showWhen)
    }

    @Test fun `paused endAtWall reanchored value is passed through (Chronometer off field retained)`() {
        val paused = engine.pause(running30(), now + 60_000L)
        val c = NotificationContent.build(paused.copy(endAtWall = 5_000_000L), 5)
        assertEquals(5_000_000L, c.endAtWall)
        assertFalse(c.showWhen)
    }

    @Test fun `idle is inactive not counting showWhen false endAtWall 0`() {
        val c = NotificationContent.build(TimerState(), 5)
        assertFalse(c.active); assertFalse(c.counting); assertFalse(c.showWhen)
        assertEquals(0L, c.endAtWall)
        assertEquals(0L, c.pausedRemainingMs)
    }

    @Test fun `paused has pausedRemainingMs as frozen remaining totalMs passed through custom false`() {
        // start 30 minutes, pause after 1 minute -> frozen remaining 29 minutes
        val paused = engine.pause(running30(), now + 60_000L)
        val c = NotificationContent.build(paused, 5)
        assertEquals(29 * 60_000L, c.pausedRemainingMs)
        assertEquals(30 * 60_000L, c.totalMs)
        assertFalse(c.custom)
    }

    @Test fun `running has pausedRemainingMs 0 (countdown rendered by system Chronometer not body)`() {
        assertEquals(0L, NotificationContent.build(running30(), 5).pausedRemainingMs)
    }

    @Test fun `custom duration passes through custom and totalMs (determines remaining format consistent with home)`() {
        val custom = engine.start(TimerState(choice = Choice.Custom(90 * 60_000L)), now, nowWall)
        val c = NotificationContent.build(custom, 5)
        assertTrue(c.custom)
        assertEquals(90 * 60_000L, c.totalMs)
        assertEquals(0L, c.pausedRemainingMs)
    }

    @Test fun `stepMinutes passed through without clamping (clamped at SettingsStore layer)`() {
        assertEquals(1, NotificationContent.build(running30(), 1).extendMinutes)
        assertEquals(30, NotificationContent.build(running30(), 30).extendMinutes)
    }
}
