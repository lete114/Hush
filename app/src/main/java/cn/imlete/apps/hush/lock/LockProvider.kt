package cn.imlete.apps.hush.lock

import android.content.Context

class LockProvider(private val context: Context) {
    /**
     * The capability truth = whether the device admin is active. `isAdminActive` is a
     * synchronous binder call, available on the spot in the brand-new process started by the
     * alarm — unlike the accessibility path, which had to wait for the service to reconnect
     * (spec §4).
     */
    fun capability(): LockCapability =
        decideLockCapability(daActive = DeviceAdminLock.isActive(context))

    /** Locks the screen based on the capability probe; returns whether the lock was successfully initiated. */
    fun lock(): Boolean {
        // Pause first (while the target app's session is still alive), then lock: after the
        // screen is locked, the app may release its session in its own onStop, and the media
        // button session would no longer be reachable. Independent of whether the lock succeeds
        // (spec §3).
        MediaPause.dispatchIfEnabled(context)
        return when (capability()) {
            LockCapability.DEVICE_ADMIN -> DeviceAdminLock.lock(context)
            LockCapability.NONE -> false
        }
    }
}
