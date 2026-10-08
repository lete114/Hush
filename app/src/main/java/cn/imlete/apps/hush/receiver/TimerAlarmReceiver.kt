package cn.imlete.apps.hush.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.lock.LockFailureNotifier
import cn.imlete.apps.hush.lock.LockProvider

/**
 * The end-of-countdown lock alarm (the exact alarm is the fallback path for screen-off /
 * Doze / process death).
 *
 * This used to do a bounded retry per spec 2026-10-07 §3 (1s→3s): when the alarm starts a
 * brand-new process, the accessibility service's `onServiceConnected` had not been called
 * back yet, and the first lock() failure was swallowed by clearTimer() (silent failure).
 * The accessibility path has been removed entirely (spec 2026-10-07-remove-accessibility-lock) —
 * the device-admin test `isAdminActive` is a synchronous binder call, immediately available in
 * the new process, so the race no longer exists and the retry was deleted in the same batch.
 *
 * If `lock()` returns false (admin revoked mid-round / lockNow threw) → send a one-shot
 * failure notification, then clear the round (spec 2026-10-07-lock-failure-notification §3.1);
 * the round still ends unconditionally (2026-10-05 decision).
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = SettingsStore(context)
        if (!store.running || store.paused) return
        if (!LockProvider(context).lock()) LockFailureNotifier.notify(context)
        store.clearTimer()
    }
}
