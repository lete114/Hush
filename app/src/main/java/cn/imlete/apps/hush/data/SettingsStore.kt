package cn.imlete.apps.hush.data

import android.content.Context
import cn.imlete.apps.hush.util.AppLocale

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hush", Context.MODE_PRIVATE)

    var endAtWall: Long
        get() = prefs.getLong(KEY_END_AT_WALL, 0)
        set(v) = prefs.edit().putLong(KEY_END_AT_WALL, v).apply()

    var totalMs: Long
        get() = prefs.getLong(KEY_TOTAL, 0)
        set(v) = prefs.edit().putLong(KEY_TOTAL, v).apply()

    var running: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(v) = prefs.edit().putBoolean(KEY_RUNNING, v).apply()

    var paused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(v) = prefs.edit().putBoolean(KEY_PAUSED, v).apply()

    /** Remaining milliseconds frozen while paused (`TimerState.endAt`); restored from this after a cross-process rebuild. */
    var pausedRemainingMs: Long
        get() = prefs.getLong(KEY_PAUSED_REMAINING, 0)
        set(v) = prefs.edit().putLong(KEY_PAUSED_REMAINING, v).apply()

    var choiceType: String
        get() = prefs.getString(KEY_CHOICE_TYPE, "off") ?: "off"
        set(v) = prefs.edit().putString(KEY_CHOICE_TYPE, v).apply()

    var choiceDurationMs: Long
        get() = prefs.getLong(KEY_CHOICE_DURATION, 0)
        set(v) = prefs.edit().putLong(KEY_CHOICE_DURATION, v).apply()

    var customDurationMs: Long
        get() = prefs.getLong(KEY_CUSTOM, 0)
        set(v) = prefs.edit().putLong(KEY_CUSTOM, v).apply()

    /** ± adjustment step (minutes). Reads and writes are both clamped to 1..30, guarding against out-of-range values from old builds/corrupt data. */
    var stepMinutes: Int
        get() = prefs.getInt(KEY_STEP_MINUTES, DEFAULT_STEP_MINUTES).coerceIn(MIN_STEP_MINUTES, MAX_STEP_MINUTES)
        set(v) = prefs.edit().putInt(KEY_STEP_MINUTES, v.coerceIn(MIN_STEP_MINUTES, MAX_STEP_MINUTES)).apply()

    /** D (spec 2026-10-05-recents-hide): whether to hide this app in recents; **default on**. */
    var hideFromRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, true)
        set(v) = prefs.edit().putBoolean(KEY_HIDE_RECENTS, v).apply()

    /** Pause media on time-up (spec 2026-10-06-pause-media-on-lock): **default off** (so those who rely on bedtime audio are not disturbed). */
    var pauseMediaOnLock: Boolean
        get() = prefs.getBoolean(KEY_PAUSE_MEDIA_ON_LOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_PAUSE_MEDIA_ON_LOCK, v).apply()

    /** UI language (spec 2026-10-06-i18n-design §4): `en` | `zh`, default en; invalid values are normalized to en on both reads and writes. */
    var appLocale: String
        get() = AppLocale.resolveLocaleTag(prefs.getString(KEY_APP_LOCALE, null))
        set(v) = prefs.edit().putString(KEY_APP_LOCALE, AppLocale.resolveLocaleTag(v)).apply()

    fun clearTimer() {
        prefs.edit().remove(KEY_END_AT_WALL).remove(KEY_TOTAL).remove(KEY_PAUSED_REMAINING)
            .putBoolean(KEY_RUNNING, false).putBoolean(KEY_PAUSED, false)
            .remove(KEY_PENDING_LOCK) // Cleanup of a leftover from old versions (cross-cold-start lock catch-up); the new logic no longer has this bit
            .apply()
    }

    internal companion object {
        const val KEY_END_AT_WALL = "endAtWall"
        const val KEY_TOTAL = "totalMs"
        const val KEY_RUNNING = "running"
        const val KEY_PAUSED = "paused"
        const val KEY_PAUSED_REMAINING = "pausedRemainingMs"
        const val KEY_PENDING_LOCK = "pendingLock"
        const val KEY_CHOICE_TYPE = "choiceType"
        const val KEY_CHOICE_DURATION = "choiceDurationMs"
        const val KEY_CUSTOM = "customDurationMs"
        const val DEFAULT_STEP_MINUTES = 5
        const val MIN_STEP_MINUTES = 1
        const val MAX_STEP_MINUTES = 30
        const val KEY_STEP_MINUTES = "stepMinutes"
        const val KEY_HIDE_RECENTS = "hideFromRecents"
        const val KEY_PAUSE_MEDIA_ON_LOCK = "pauseMediaOnLock"
        const val KEY_APP_LOCALE = "appLocale"
    }
}
