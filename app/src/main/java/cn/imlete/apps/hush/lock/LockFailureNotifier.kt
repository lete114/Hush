package cn.imlete.apps.hush.lock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import cn.imlete.apps.hush.MainActivity
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.service.CountdownService
import cn.imlete.apps.hush.withAppLocale

/**
 * Screen-lock failure notification (spec 2026-10-07-lock-failure-notification §3.2).
 *
 * Sends a one-shot notification when the countdown fires and [LockProvider.lock] returns false
 * (admin revoked mid-round / lockNow threw): tapping it → [MainActivity] → the cold start
 * surfaces the existing capability guidance dialog "Lock not ready", so the repair path
 * adds zero new code.
 *
 * Fixed notification ID = 2 (countdown FGS = 1): with the same ID, a later notification
 * replaces the earlier one, so races between the two paths never stack; the ticker path's
 * subsequent `stopForeground(REMOVE)` only removes ID 1, so this notification survives.
 * Swallows exceptions entirely — never crash because of a hint (on 13+, without notification
 * permission the system silently drops it; known limitation, see spec §4).
 */
object LockFailureNotifier {

    private const val CHANNEL_ID = "hush_lock_failed"
    private const val NOTIFICATION_ID = 2

    fun notify(context: Context) {
        try {
            val ctx = context.withAppLocale() // text follows the in-app language (same as the countdown notification)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.lock_failed_channel_name),
                    NotificationManager.IMPORTANCE_HIGH, // a failure needs immediate attention; independent of hush_countdown/LOW
                ).apply { description = ctx.getString(R.string.lock_failed_channel_desc) }
                ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }
            val openApp = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java)
                    .setAction(CountdownService.ACTION_OPEN_NOTIFICATION) // reuse the existing notification-tap channel → lands on the home screen, closes the overlay
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP, // an existing instance receives onNewIntent to be brought to front; prevents instance stacking
                    ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(ctx.getString(R.string.notif_lock_failed_title))
                .setContentText(ctx.getString(R.string.notif_lock_failed_msg))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            ctx.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
            // The notification is a nice-to-have: any failure (including the 13+ no-permission
            // path) must not crash the caller
        }
    }
}
