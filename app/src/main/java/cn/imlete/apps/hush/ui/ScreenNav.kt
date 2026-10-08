package cn.imlete.apps.hush.ui

/**
 * Page navigation targets. **The enum order is the browsing hierarchy**: home → settings → about.
 *
 * Moved out of the `private enum` in `MainActivity.kt` so [isForward] and `MainActivity` share it
 * (spec 2026-10-06-screen-transition §3).
 */
internal enum class Screen { HOME, SETTINGS, ABOUT }

/**
 * Decides whether this transition is forward or back — the sole basis for the transition direction:
 * `true` the new page slides in from the right (the old one exits left), `false` the new page slides in from the left (the old one exits right).
 *
 * The criterion is whether the enum ordinal increases, consistent with the browsing hierarchy. A notification tap jumping to home is also the back direction.
 *
 * Pure function: **imports no `android.*` / `androidx.*`**, guaranteeing JVM unit-testability (repo hard rule).
 */
internal fun isForward(from: Screen, to: Screen): Boolean = to.ordinal > from.ordinal
