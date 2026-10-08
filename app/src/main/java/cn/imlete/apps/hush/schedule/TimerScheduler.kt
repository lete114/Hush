package cn.imlete.apps.hush.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import cn.imlete.apps.hush.receiver.TimerAlarmReceiver

class TimerScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(endAtWall: Long) {
        val am = alarmManager ?: return
        cancel()
        val pi = pendingIntent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtWall, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtWall, pi)
        }
    }

    /** Whether the exact alarm is available (API 31+ requires the user to grant SCHEDULE_EXACT_ALARM; below 31 it is always true). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun cancel() = alarmManager?.cancel(pendingIntent())

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, TimerAlarmReceiver::class.java).setAction(ACTION_FINISH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_FINISH = "cn.imlete.apps.hush.action.FINISH"
        private const val REQUEST_CODE = 1001
    }
}
