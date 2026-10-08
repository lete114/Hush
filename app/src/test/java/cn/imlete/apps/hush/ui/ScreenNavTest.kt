package cn.imlete.apps.hush.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Screen transition direction (spec 2026-10-06-screen-transition §3): increasing enum ordinal means forward. */
class ScreenNavTest {

    @Test fun `home to settings is forward`() = assertTrue(isForward(Screen.HOME, Screen.SETTINGS))

    @Test fun `settings to about is forward`() = assertTrue(isForward(Screen.SETTINGS, Screen.ABOUT))

    @Test fun `about to settings is backward`() = assertFalse(isForward(Screen.ABOUT, Screen.SETTINGS))

    @Test fun `settings to home is backward`() = assertFalse(isForward(Screen.SETTINGS, Screen.HOME))

    /** Notification click jumps to home (MainActivity.kt): ordinal decreases -> backward transition animation. */
    @Test fun `notification jump to home treated as backward`() = assertFalse(isForward(Screen.ABOUT, Screen.HOME))

    @Test fun `same screen is not forward`() = assertFalse(isForward(Screen.HOME, Screen.HOME))
}
