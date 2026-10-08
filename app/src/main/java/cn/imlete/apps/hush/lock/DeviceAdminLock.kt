package cn.imlete.apps.hush.lock

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

object DeviceAdminLock {
    fun component(context: Context) = ComponentName(context, HushAdminReceiver::class.java)

    fun isActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java)?.isAdminActive(component(context)) == true

    fun lock(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isAdminActive(component(context))) return false
        return try {
            dpm.lockNow()
            true
        } catch (_: Throwable) {
            // spec 2026-10-07-lock-failure-notification §3.1: the exception is propagated to the
            // caller as a failure so it goes through the failure-notification flow; never let the
            // exception crash the new process of CountdownService / receiver (worse than silence).
            false
        }
    }
}
