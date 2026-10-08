package cn.imlete.apps.hush.lock

/**
 * Screen-lock capability (spec 2026-10-07-remove-accessibility-lock-design §4).
 *
 * The accessibility path has been removed entirely, so the capability depends solely on
 * whether the device admin is active — a two-value enum:
 * [DEVICE_ADMIN] = can lock the screen, [NONE] = cannot lock when the timer fires
 * (the UI shows a guidance dialog accordingly).
 */
enum class LockCapability { DEVICE_ADMIN, NONE }

fun decideLockCapability(daActive: Boolean): LockCapability =
    if (daActive) LockCapability.DEVICE_ADMIN else LockCapability.NONE
