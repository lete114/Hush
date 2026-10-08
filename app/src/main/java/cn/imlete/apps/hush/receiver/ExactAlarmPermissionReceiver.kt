package cn.imlete.apps.hush.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.schedule.TimerScheduler
import cn.imlete.apps.hush.service.CountdownService

/**
 * Callback for the "Alarms & reminders" permission grant (spec 2026-10-07-exact-alarm-permission-rearm §3.1).
 *
 * The system only sends this broadcast when SCHEDULE_EXACT_ALARM is **granted**; it is not sent
 * on revocation, and revoking deletes the exact alarm and kills the process (there is no
 * app-level revocation hook — the revocation side is scoped as "user behavior" and not fixed,
 * spec §2.2). On receipt, if an active round is running:
 * ① [TimerScheduler.schedule] re-arms — the permission is in hand now → exact; if endAtWall
 * has already passed → delivered immediately → the existing [TimerAlarmReceiver] locks late
 * (a late lock > no lock, spec §2.2 G2).
 * ② [CountdownService.startForeground] resurrects the service — if the process was dead →
 * the ticker and the countdown notification come back; if already running → an idempotent
 * ACTION_SYNC nudge.
 *
 * On lower versions (< API 31) this action does not exist and is never broadcast, so the
 * receiver naturally never fires (spec §4).
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val store = SettingsStore(context)
        // Guard identical to TimerAlarmReceiver's, plus a dirty-data guard on endAtWall
        // (prevents schedule(0) firing immediately and locking the screen by mistake)
        if (!store.running || store.paused || store.endAtWall <= 0L) return
        TimerScheduler(context).schedule(store.endAtWall)
        try {
            CountdownService.startForeground(context)
        } catch (_: IllegalStateException) {
            // Background FGS start restriction (ForegroundServiceStartNotAllowedException is its
            // subclass; the same precedent as syncIfRunning): the alarm is already armed, so at
            // the deadline the screen is still locked via TimerAlarmReceiver; only the
            // notification is not resurrected = the known degradation in spec §4
        }
    }
}
