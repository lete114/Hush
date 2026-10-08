package cn.imlete.apps.hush.lock

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lock capability decision (spec 2026-10-07-remove-accessibility-lock-design §4).
 *
 * The accessibility path has been removed entirely; capability depends only on whether the
 * device admin is active — a two-value enum. `NONE` means "cannot lock when the time comes",
 * which triggers the onboarding dialog in the UI.
 */
class LockCapabilityTest {
    @Test fun `lock available when device admin active`() =
        assertEquals(LockCapability.DEVICE_ADMIN, decideLockCapability(daActive = true))

    @Test fun `no lock capability when device admin is inactive`() =
        assertEquals(LockCapability.NONE, decideLockCapability(daActive = false))
}
