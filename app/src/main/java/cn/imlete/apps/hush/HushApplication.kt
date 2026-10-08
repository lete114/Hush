package cn.imlete.apps.hush

import android.app.Application
import android.content.Context

/**
 * Process-level entry point: decides whether this opening of the UI is a new process.
 *
 * For the semantics see spec `2026-10-05-cold-start-discard-round-design.md`:
 * clearing from recents / restarting ≈ closing the app, so **any active round left over after the process
 * comes back up is discarded and never gets a catch-up lock**.
 * Android has no query API for "I was force-stopped", so "this process's very first creation" is used as the signal.
 */
class HushApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        processFresh = true
    }

    companion object {
        /** Set exactly once per process; consumed by [HomeViewModel] (a read invalidates it), so an Activity recreation within the same process is not misjudged. */
        @Volatile
        private var processFresh: Boolean = false

        /** Returns true meaning "this process just started"; true only on the first call. */
        fun consumeProcessFresh(): Boolean {
            if (!processFresh) return false
            processFresh = false
            return true
        }
    }
}
