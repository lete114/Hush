package cn.imlete.apps.hush.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cn.imlete.apps.hush.MainActivity
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.data.loadState
import cn.imlete.apps.hush.data.saveState
import cn.imlete.apps.hush.lock.LockFailureNotifier
import cn.imlete.apps.hush.lock.LockProvider
import cn.imlete.apps.hush.schedule.TimerScheduler
import cn.imlete.apps.hush.timer.AlarmOp
import cn.imlete.apps.hush.timer.CountdownActions
import cn.imlete.apps.hush.timer.CountdownEvent
import cn.imlete.apps.hush.timer.NotificationContent
import cn.imlete.apps.hush.timer.TimerEngine
import cn.imlete.apps.hush.util.DurationFormatter
import cn.imlete.apps.hush.withAppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Countdown foreground service (spec: 2026-10-04-fgs-notification-countdown-design).
 *
 * Responsibilities: keep the countdown notification alive (the system Chronometer renders
 * the remaining time), handle notification-button events, and run a self-owned ticker to
 * detect the deadline and lock the screen (HyperOS default policy delays broadcasts, so the
 * in-process ticker is the primary path; the exact-alarm receiver is the screen-off / Doze
 * fallback).
 *
 * Business rules always go through [CountdownActions] / [TimerEngine]; this class only does
 * the Android glue work (store read/write, alarm re-arm, notification refresh, PendingIntent).
 */
class CountdownService : Service() {

    private val store by lazy { SettingsStore(this) }
    private val engine = TimerEngine()
    private val scheduler by lazy { TimerScheduler(this) }
    private val lockProvider by lazy { LockProvider(this) }
    // Main: symmetric with HomeViewModel's per-tick main-thread refresh (prefs are cached in
    // memory, and the lock binder call goes through the same path as the VM); it also guarantees
    // that service-lifecycle calls such as startForeground/stopSelf always stay on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    /** Whether startForeground has already been called (tracks the startForegroundService contract; see the race fallback in sync()). */
    private var promoted = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> handleEvent(CountdownEvent.Pause)
            ACTION_RESUME -> handleEvent(CountdownEvent.Resume)
            ACTION_EXTEND -> handleEvent(CountdownEvent.Extend)
            ACTION_CANCEL -> handleEvent(CountdownEvent.Cancel)
            else -> Unit // ACTION_SYNC: rebuild from the store only
        }
        sync()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        tickerJob?.cancel()
        scope.cancel() // kotlinx.coroutines.cancel: cancels the entire scope
        super.onDestroy()
    }

    /** Notification-button event → pure logic derivation → persist to store / alarm / stop service. */
    private fun handleEvent(event: CountdownEvent) {
        val now = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val outcome = CountdownActions.decide(store.loadState(now, nowWall), event, stepMs(), now, nowWall)
        if (outcome.changed) store.saveState(outcome.state)
        when (outcome.alarm) {
            AlarmOp.SCHEDULE -> scheduler.schedule(outcome.state.endAtWall)
            AlarmOp.CANCEL -> scheduler.cancel()
            AlarmOp.NONE -> Unit
        }
        if (outcome.shouldLock) lockAndCleanup()
        // outcome.stopService → sync() stops the service once it reads the idle state; not hardcoded here
    }

    /** Refresh the foreground notification from the store's current state; exit the service when idle. */
    private fun sync() {
        createChannel() // idempotent: createNotificationChannel with the same ID allows updating the name/description → channel text refreshes with the current language after a language switch
        val now = SystemClock.elapsedRealtime()
        val content = NotificationContent.build(store.loadState(now, System.currentTimeMillis()), store.stepMinutes)
        if (!content.active) {
            // Race fallback: if the state was already cleared by the time the startForegroundService
            // command arrives (the alarm receiver locked first / the user cancelled extremely
            // fast), calling stopSelf without having promoted would trigger
            // ForegroundServiceDidNotStartInTimeException — satisfy the 5s contract first, then
            // stop (the notification is removed immediately, with no visible flash).
            if (!promoted) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, bareNotification(), foregroundType())
                promoted = true
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Calling startForeground again with the same id refreshes the notification (no separate
        // notify needed; this also avoids the SecurityException that notify would throw when
        // the permission is missing)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(content), foregroundType())
        promoted = true
        startTicker()
    }

    private fun foregroundType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0

    /** Minimal notification for the race fallback (only satisfies the foreground contract; removed right after by stopForeground). */
    private fun bareNotification(): Notification {
        val ctx = withAppLocale()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setOngoing(true)
            .build()
    }

    /** In-process ticker (primary path): at the deadline it runs the CountdownActions.Expire pure
     *  derivation → lock → exit the service; it also covers the idle-ization of "the alarm
     *  receiver locked first" — once the state is found idle it exits the service by itself to
     *  prevent leftover notifications. Heartbeat [SERVICE_TICK_MS] (1s): both the deadline
     *  fallback and the idle self-stop are detected within ≤1s; the exact-alarm leg, which is
     *  millisecond-level, is unaffected. */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(SERVICE_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val state = store.loadState(now, System.currentTimeMillis())
                if (!state.running && !state.paused) {
                    // receiver path (path ②) or the state was cleared externally: the notification should disappear and the service should exit
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                if (state.running && !state.paused && engine.remainingMs(state, now) <= 0L) {
                    handleEvent(CountdownEvent.Expire) // decide → shouldLock → lockAndCleanup + cancel the alarm
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
            }
        }
    }

    /** Guard and cleanup identical to TimerAlarmReceiver's, plus alarm cancellation (a superset: on the receiver path the alarm has already fired and is not cancelled; idempotent — a repeated lock is harmless). */
    private fun lockAndCleanup() {
        if (!store.running || store.paused) return
        // The round ends whether the lock succeeds or not (spec 2026-10-05): no longer set
        // pendingLock to retry the lock across a cold start.
        val locked = lockProvider.lock()
        if (!locked) LockFailureNotifier.notify(this) // failure visibility (spec 2026-10-07-lock-failure-notification)
        store.clearTimer()
        scheduler.cancel()
    }

    private fun stepMs(): Long = store.stepMinutes * 60_000L

    // ---- Notification building ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = withAppLocale()
            val channel = NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = ctx.getString(R.string.channel_desc) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(c: NotificationContent): Notification {
        val ctx = withAppLocale()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(ACTION_OPEN_NOTIFICATION) // spec §4: onNewIntent identifies a notification tap via this
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP, // an existing instance receives onNewIntent to be brought to front; prevents instance stacking
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(c.showWhen) // true in the running state (Chronometer uses when as its base); false in the paused state hides the fake clock
            .setContentIntent(openApp)
            // Since Android 14 a setOngoing notification can be swiped away by the user (spec §2.1 fact 1):
            // swiping it away → ACTION_SYNC idempotently rebuilds and re-posts it.
            // Official javadoc: the app's own cancel() does not fire this intent (fact 3) → a
            // normally finished round does not resurrect the notification.
            .setDeleteIntent(eventIntent(ACTION_SYNC))

        if (c.counting) {
            builder
                .setWhen(c.endAtWall) // Chronometer counts down to the wall-clock deadline (kept; not display text)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(R.drawable.ic_pause, ctx.getString(R.string.action_pause), eventIntent(ACTION_PAUSE))
                .addAction(R.drawable.ic_plus, ctx.getString(R.string.action_extend, c.extendMinutes), eventIntent(ACTION_EXTEND))
        } else {
            builder
                // The paused-state body carries the frozen remaining (the value does not change while paused
                // → static rendering, no per-second refresh problem);
                // the format follows the same formatCountdown rules as the home-screen center
                // label (spec §3 paused row)
                .setContentText(
                    ctx.getString(
                        R.string.notif_paused_remaining,
                        DurationFormatter.formatCountdown(c.custom, c.totalMs, c.pausedRemainingMs),
                    ),
                )
                .setUsesChronometer(false)
                .addAction(R.drawable.ic_play, ctx.getString(R.string.action_resume), eventIntent(ACTION_RESUME))
                .addAction(R.drawable.ic_plus, ctx.getString(R.string.action_extend, c.extendMinutes), eventIntent(ACTION_EXTEND))
        }
        builder.addAction(R.drawable.ic_close, ctx.getString(R.string.action_cancel), eventIntent(ACTION_CANCEL))
        return builder.build()
    }

    /** Notification button → service command. API 26+ uses getForegroundService: even if the
     *  service has died unexpectedly, the button still starts it as a foreground service
     *  (avoiding the background startService restriction); the 5s contract is covered by the
     *  promoted fallback in sync(). API 24–25 has no background-start restriction, so it falls
     *  back to getService. requestCode distinguishes actions via action.hashCode(). */
    private fun eventIntent(action: String): PendingIntent {
        val intent = Intent(this, CountdownService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, action.hashCode(), intent, flags)
        } else {
            PendingIntent.getService(this, action.hashCode(), intent, flags)
        }
    }

    companion object {
        const val CHANNEL_ID = "hush_countdown"
        const val NOTIFICATION_ID = 1
        const val ACTION_SYNC = "cn.imlete.apps.hush.action.SYNC"
        const val ACTION_PAUSE = "cn.imlete.apps.hush.action.PAUSE"
        const val ACTION_RESUME = "cn.imlete.apps.hush.action.RESUME"
        const val ACTION_EXTEND = "cn.imlete.apps.hush.action.EXTEND"
        const val ACTION_CANCEL = "cn.imlete.apps.hush.action.CANCEL"
        const val ACTION_OPEN_NOTIFICATION = "cn.imlete.apps.hush.action.OPEN_NOTIFICATION"

        /** Service heartbeat period (spec 2026-10-07-fgs-ticker-notification-dismiss §3.1): it only
         *  handles the "deadline Expire fallback" and the "self-stop after the receiver already
         *  locked" — second-level granularity is enough. Not sharing its source with ui.Ticker's
         *  TICK_MS — that one is constrained by sharing its source with the ring tween (see its
         *  KDoc), and the UI is out of scope for this batch.
         *  Does not change "long-sleep until the deadline": the delay clock excludes deep sleep
         *  (deep sleep would make the sleeping time untracked → late lock after wake-up); every
         *  poll tick re-aligns against elapsedRealtime(), so after waking up it recovers within
         *  ≤1 tick. */
        private const val SERVICE_TICK_MS = 1_000L

        /** Service-instance liveness flag (shared within the process, maintained in onCreate/onDestroy), so syncIfRunning can decide whether to nudge. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** UI → service: nudge only when the service is already running (does not trigger a foreground start; no-op when not running, to avoid an unnecessary short-lived service instance). */
        fun syncIfRunning(context: Context) {
            if (!isRunning) return
            try {
                context.startService(Intent(context, CountdownService::class.java).setAction(ACTION_SYNC))
            } catch (_: IllegalStateException) {
                // Background-start restriction: the notification refresh is left to the next start()/button
                // event; not blocking
            }
        }

        /** Exclusively for start()/resume()/init recovery: start the service in the foreground
         *  (within 5s onStartCommand → sync → startForeground).
         *  ContextCompat uses startForegroundService on API 26+, and falls back to startService
         *  on API 24–25 (minSdk) — the lower versions have no background-start restriction, and
         *  the service still promotes itself to foreground within onStartCommand → sync. */
        fun startForeground(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CountdownService::class.java).setAction(ACTION_SYNC),
            )
        }
    }
}
