package cn.imlete.apps.hush.lock

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import cn.imlete.apps.hush.data.SettingsStore

/**
 * Pause media when the countdown fires (spec 2026-10-06-pause-media-on-lock).
 *
 * Mechanism = deliver one media pause key to the media button session. `AudioManager.dispatchMediaKeyEvent`
 * is a public API that needs zero permissions and also works in background processes
 * (probe-tested; see the probe factual-basis section in the spec).
 *
 * Best-effort: sources without a MediaSession cannot be targeted, and when multiple apps play
 * at the same time only the highest-priority one is paused.
 * Therefore **no failure may affect the lock** — swallow exceptions overall, never throw outward.
 */
object MediaPause {

    /** Return immediately if the setting is off; otherwise send `KEYCODE_MEDIA_PAUSE` down+up. Swallows exceptions, never throws. */
    fun dispatchIfEnabled(context: Context) {
        // Reading the setting is also inside the try: if SharedPreferences is corrupted / the
        // Context environment is unavailable, rather silently skip the pause than ever let the
        // lock path throw.
        try {
            if (!SettingsStore(context).pauseMediaOnLock) return

            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // Hard constraint: only KEYCODE_MEDIA_PAUSE may be used; KEYCODE_MEDIA_PLAY_PAUSE is
            // forbidden — the latter is a toggle and would start playback on an already-paused
            // player (the reverse accident in the screen-off scenario).
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (_: Throwable) {
            // Some ROMs may throw on binder calls; a failed pause does not affect the lock.
        }
    }
}
