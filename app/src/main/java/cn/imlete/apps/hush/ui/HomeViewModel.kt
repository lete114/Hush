package cn.imlete.apps.hush.ui

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.imlete.apps.hush.HushApplication
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.withAppLocale
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.data.loadState
import cn.imlete.apps.hush.data.saveState
import cn.imlete.apps.hush.data.selectedDurationOf
import cn.imlete.apps.hush.lock.LockCapability
import cn.imlete.apps.hush.lock.LockProvider
import cn.imlete.apps.hush.schedule.TimerScheduler
import cn.imlete.apps.hush.service.CountdownService
import cn.imlete.apps.hush.timer.Choice
import cn.imlete.apps.hush.timer.CountdownActions
import cn.imlete.apps.hush.timer.CountdownEvent
import cn.imlete.apps.hush.timer.TimerEngine
import cn.imlete.apps.hush.timer.TimerState
import cn.imlete.apps.hush.timer.hasChosenDuration
import cn.imlete.apps.hush.util.DurationFormatter

/**
 * Home screen state holder. Wires [TimerEngine] (pure derivation), [SettingsStore] (persistence),
 * [TimerScheduler] (AlarmManager) and [LockProvider] (lock-capability probing) together.
 *
 * Not an AndroidViewModel: it directly holds applicationContext and lives with the composition's `remember`.
 * Pure logic still lives in `timer/` (JVM-unit-testable); this layer only does wiring and state exposure.
 */
class HomeViewModel(context: Context) {

    private val appContext = context.applicationContext
    private val engine = TimerEngine()
    private val store = SettingsStore(appContext)
    private val scheduler = TimerScheduler(appContext)
    private val lockProvider = LockProvider(appContext)

    /** Timer state. The initial value is rebuilt from [SettingsStore] (while running `endAt = now + (endAtWall − nowWall)`; while paused it uses the frozen remaining). */
    var timerState: TimerState by mutableStateOf(loadFromStore())
        private set

    /** Set when `start()` detects [LockCapability.NONE]; the UI pops the guidance dialog accordingly. */
    var showLockDialog: Boolean by mutableStateOf(false)
        private set

    /** ± step (minutes). Changes persist immediately; the slider writes directly here. */
    var stepMinutes: Int by mutableStateOf(store.stepMinutes)
        private set

    /**
     * Exact-alarm availability (spec 2026-10-05-reliability-guardrails):
     * API 31+ requires the user to authorize under "Alarms & reminders"; otherwise it degrades to an inexact alarm (the time-up may drift);
     * API < 31 is always available. Re-read every tick by [refresh].
     */
    var exactAlarmReady: Boolean by mutableStateOf(scheduler.canScheduleExact())
        private set

    /** Battery-optimization whitelist: if not on the list → the system may kill the background at any time and no one wakes the screen at time-up. */
    var batteryWhitelisted: Boolean by mutableStateOf(isBatteryWhitelisted())
        private set

    fun setStep(minutes: Int) {
        store.stepMinutes = minutes
        stepMinutes = store.stepMinutes
        // Changing the step while running → the notification's "+N minutes" label must refresh to the new step (spec §4.2, final review A15).
        CountdownService.syncIfRunning(appContext)
    }

    private fun stepMs(): Long = store.stepMinutes * 60_000L

    init {
        // Reopening after clearing from recents / restarting (= new process) → any timing left over from the previous round is discarded: no lock, no prompt, alarm cancelled.
        // See spec 2026-10-05-cold-start-discard-round-design.md for details.
        if (HushApplication.consumeProcessFresh()) discardRoundOnColdStart()
        // Reopening the app after the process was killed restores the foreground notification (spec §12.3); when the service is not running, this call is a foreground start.
        // (The state just discarded above is idle, so it cannot reach this branch.)
        if (timerState.running || timerState.paused) CountdownService.startForeground(appContext)
    }

    /**
     * New-process cold start: any active round is discarded — clear the round fields (keeping the selected duration), cancel the alarm, do not raise a foreground service.
     * The decision lives in the [CountdownActions] pure layer (`ColdStart` event, `shouldLock` is always false; never a catch-up lock).
     */
    private fun discardRoundOnColdStart() {
        val outcome = CountdownActions.decide(
            state = timerState,
            event = CountdownEvent.ColdStart,
            stepMs = stepMs(),
            now = SystemClock.elapsedRealtime(),
            nowWall = System.currentTimeMillis(),
        )
        if (!outcome.changed) return
        timerState = outcome.state
        persist()
        scheduler.cancel()
    }

    /** Re-reads timer state from persistence. Time-up/failure is written to the store by the receiver; resume refreshes the UI from it. */
    fun refresh() {
        // Re-query the binder only while the guidance box is open: the user returning after granting it in system settings → it clears automatically; it only goes down, never up (spec §1 rule 3).
        if (showLockDialog && lockProvider.capability() != LockCapability.NONE) showLockDialog = false
        timerState = loadFromStore()
        stepMinutes = store.stepMinutes
        // Reliability status row (spec 2026-10-05-reliability-guardrails): re-read every tick so returning from a system page is reflected immediately.
        exactAlarmReady = scheduler.canScheduleExact()
        batteryWhitelisted = isBatteryWhitelisted()
    }

    /** Battery whitelist status; when a particular ROM throws SecurityException, treat it as not whitelisted (conservatively show the guidance entry). */
    private fun isBatteryWhitelisted(): Boolean = try {
        appContext.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(appContext.packageName)
    } catch (_: Exception) {
        false
    }

    fun uiState(now: Long, nowWall: Long): HomeUiState {
        // Gauge text resolution must wrap a fresh language context: switching language only recreates the Activity,
        // and the applicationContext's attachBaseContext wrapping stops at cold start, so using it directly would stay stuck in the old language (spec §4).
        val strCtx = appContext.withAppLocale()
        val s = timerState
        val active = s.running || s.paused

        // While running and while paused, the remaining time is shown (when paused, endAt is the frozen remaining); while idle and not Off, the selected duration is shown.
        // The format is decided by "choice type + total duration" (UI_SPEC §7): only custom and >60 minutes uses HH:MM:SS,
        // everything else stays MM:SS — the character count of a whole round never changes, e.g. selecting 30 minutes and tapping start shows 30:00 → 29:59 rather than
        // 00:29:59 (design §3.1). Off (necessarily idle: start is blocked by hasChosenDuration) always shows 00:00 when idle,
        // without falling back to the 30-minute placeholder (spec 2026-10-05-drawer-copy-defaults §③;
        // the DEFAULT_DURATION_MS placeholder is kept for persistence/estimation).
        // The selected duration on the idle path is computed only once: shared by the center label and the "ends at" estimate (neither uses it when active).
        val selectedMs = if (!active) selectedDurationOf(s) else 0L
        val centerLabel = if (s.choice == Choice.Off && !active) {
            "00:00"
        } else {
            val totalMs = if (active) s.totalMs else selectedMs
            val valueMs = if (active) engine.remainingMs(s, now) else selectedMs
            DurationFormatter.formatCountdown(
                isCustom = s.choice is Choice.Custom,
                totalMs = totalMs,
                valueMs = valueMs,
            )
        }

        val subtitle = when {
            s.paused -> strCtx.getString(R.string.subtitle_paused)
            s.running -> strCtx.getString(R.string.subtitle_to_lock)
            s.choice == Choice.Off -> strCtx.getString(R.string.subtitle_idle)
            else -> strCtx.getString(R.string.subtitle_to_lock)
        }

        val finishAtLabel = when {
            s.choice == Choice.Off -> null
            active -> strCtx.getString(R.string.label_finish_at, DurationFormatter.formatClock(s.endAtWall))
            else -> strCtx.getString(
                R.string.label_finish_at,
                DurationFormatter.formatClock(nowWall + selectedMs),
            )
        }

        val primaryAction = when {
            s.paused -> PrimaryAction.RESUME
            s.running -> PrimaryAction.PAUSE
            else -> PrimaryAction.START
        }

        return HomeUiState(
            // The presentation layer's "in progress" = running or paused (see UI_SPEC §3.2 and gallery ④: running stays true when paused).
            // Feeding the engine's s.running directly would treat the paused state as not started — the progress arc/glow disappear, the hint text reappears, and the ± keys are disabled.
            running = active,
            paused = s.paused,
            centerLabel = centerLabel,
            subtitle = subtitle,
            progress = engine.progress(s, now),
            finishAtLabel = finishAtLabel,
            canReduce = engine.canReduce(s, now, stepMs()),
            stepMinutes = stepMinutes,
            primaryAction = primaryAction,
            // idle with no valid duration → the primary key instead opens the drawer (spec §4); running/paused is always true (meaningless except for START).
            canStart = active || hasChosenDuration(s),
            screenAlpha = 1f,
            showHint = !active,
        )
    }

    /** Start the countdown. When capabilities are missing (both DA and accessibility unavailable), only show the dialog — do not start. */
    fun start(now: Long, nowWall: Long) {
        // Defense in depth: without a valid duration, return directly (before touching the scheduler); even if someone bypasses the UI routing,
        // it will not reproduce "tapping start at 00:00 turns into 30:00" (spec 2026-10-05-idle-start-opens-drawer §4).
        if (!hasChosenDuration(timerState)) return
        if (lockProvider.capability() == LockCapability.NONE) {
            showLockDialog = true
            return
        }
        timerState = engine.start(timerState, now, nowWall)
        persist()
        scheduler.schedule(timerState.endAtWall)
        if (timerState.running) CountdownService.startForeground(appContext) // Foreground start (spec §4.1)
    }

    fun pause(now: Long, nowWall: Long) {
        timerState = engine.pause(timerState, now)
        persist()
        // On pause, re-anchor the persisted wall-clock deadline to the moment of pausing, keeping the "ends at" label stable across rebuilds.
        store.endAtWall = nowWall + timerState.endAt
        scheduler.cancel()
        CountdownService.syncIfRunning(appContext)
    }

    fun resume(now: Long, nowWall: Long) {
        timerState = engine.resume(timerState, now, nowWall)
        persist()
        reschedule()
        // spec §4.1: resume also goes through a foreground start, covering "process killed while paused and then resumed".
        if (timerState.running) CountdownService.startForeground(appContext)
    }

    fun cancel() {
        timerState = engine.off(timerState)
        persist()
        scheduler.cancel()
        CountdownService.syncIfRunning(appContext)
    }

    fun extend(now: Long) {
        timerState = engine.extend(timerState, now, stepMs())
        persist()
        reschedule()
        CountdownService.syncIfRunning(appContext)
    }

    fun reduce(now: Long) {
        timerState = engine.reduce(timerState, now, stepMs())
        persist()
        reschedule()
        CountdownService.syncIfRunning(appContext)
    }

    /** The duration selected in the drawer ([Choice.Off] / Preset / Custom). */
    fun select(choice: Choice, now: Long, nowWall: Long) {
        timerState = engine.select(timerState, choice, now, nowWall)
        persist()
        reschedule()
        CountdownService.syncIfRunning(appContext)
    }

    /** Guidance check after the entry/permission-box response (spec §1, decisions A/A1/a): set only when all capabilities are missing (NONE); no popup if any one survives. */
    fun showLockDialogIfNotReady() {
        if (lockProvider.capability() == LockCapability.NONE) showLockDialog = true
    }

    fun dismissLockDialog() {
        showLockDialog = false
    }

    private fun reschedule() {
        if (timerState.running && !timerState.paused) {
            scheduler.schedule(timerState.endAtWall)
        } else {
            scheduler.cancel()
        }
    }

    private fun persist() {
        store.saveState(timerState)
    }

    private fun loadFromStore(): TimerState =
        store.loadState(SystemClock.elapsedRealtime(), System.currentTimeMillis())
}
