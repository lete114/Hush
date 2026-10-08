package cn.imlete.apps.hush.timer

/** The six kinds of events that can occur on the service/notification side. */
sealed class CountdownEvent {
    object Pause : CountdownEvent()
    object Resume : CountdownEvent()
    object Extend : CountdownEvent()
    object Cancel : CountdownEvent()
    object Expire : CountdownEvent()

    /** Cold start (new process): clearing from recents / reboot ≈ closing the App, so any leftover active rounds are voided entirely and never re-locked. */
    object ColdStart : CountdownEvent()
}

enum class AlarmOp { NONE, SCHEDULE, CANCEL }

data class CountdownOutcome(
    val state: TimerState,
    val changed: Boolean,
    val shouldLock: Boolean,
    val alarm: AlarmOp,
    val stopService: Boolean,
)

/**
 * Event → pure derivation of the next state and side-effect directives (spec §9).
 * Only produces "what to do"; the execution (persistence / alarm / lock / stop service) is
 * done by the glue layer — business rules still live exclusively in TimerEngine.
 */
object CountdownActions {
    private val engine = TimerEngine()

    fun decide(state: TimerState, event: CountdownEvent, stepMs: Long, now: Long, nowWall: Long): CountdownOutcome =
        when (event) {
            CountdownEvent.Pause -> {
                val next = engine.pause(state, now)
                // Mirrors HomeViewModel.pause: on persistence, endAtWall is re-anchored to "pause instant + frozen remaining"
                val anchored = if (next != state) next.copy(endAtWall = nowWall + next.endAt) else next
                CountdownOutcome(anchored, anchored != state, false, if (anchored != state) AlarmOp.CANCEL else AlarmOp.NONE, false)
            }
            CountdownEvent.Resume -> {
                val next = engine.resume(state, now, nowWall)
                val alarm = if (next != state && next.running) AlarmOp.SCHEDULE else AlarmOp.NONE
                CountdownOutcome(next, next != state, false, alarm, false)
            }
            CountdownEvent.Extend -> {
                val next = engine.extend(state, now, stepMs)
                val alarm = if (next != state && next.running && !next.paused) AlarmOp.SCHEDULE else AlarmOp.NONE
                CountdownOutcome(next, next != state, false, alarm, false)
            }
            CountdownEvent.Cancel -> {
                val next = engine.off(state)
                CountdownOutcome(next, next != state, false, AlarmOp.CANCEL, true)
            }
            CountdownEvent.Expire -> CountdownOutcome(state, false, state.running && !state.paused, AlarmOp.CANCEL, false)
            // Cold-start voiding (spec 2026-10-05): active round → clear the round and cancel the alarm, but **never lock**; idle → unchanged.
            CountdownEvent.ColdStart -> if (state.running || state.paused) {
                CountdownOutcome(engine.roundOver(state), true, false, AlarmOp.CANCEL, false)
            } else {
                CountdownOutcome(state, false, false, AlarmOp.NONE, false)
            }
        }
}
