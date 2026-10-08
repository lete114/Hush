package cn.imlete.apps.hush.ui

import androidx.compose.runtime.Immutable
import cn.imlete.apps.hush.data.SettingsStore

enum class ChoiceType { OFF, PRESET, CUSTOM }

enum class PrimaryAction { START, PAUSE, RESUME }

/**
 * Presentation-only state: plain fields with no timing or derivation logic —
 * every derived value is computed upstream in `HomeViewModel.uiState`.
 */
@Immutable
data class HomeUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val centerLabel: String = "00:00",
    val subtitle: String = "Countdown not started",
    val progress: Float = 0f,
    val finishAtLabel: String? = null,
    val canReduce: Boolean = false,
    /** ± step (minutes), synced with the settings-page slider (the default value is singly sourced from SettingsStore.DEFAULT_STEP_MINUTES). */
    val stepMinutes: Int = SettingsStore.DEFAULT_STEP_MINUTES,
    val primaryAction: PrimaryAction = PrimaryAction.START,
    /**
     * Whether the countdown can start directly from the primary key. `false` = no valid duration (`Off` or 0 duration, the gauge shows 00:00),
     * in which case tapping the primary key opens the duration drawer instead (spec 2026-10-05-idle-start-opens-drawer §3).
     * Always true in the running/paused states — this field is only read when `primaryAction == START`.
     */
    val canStart: Boolean = false,
    val screenAlpha: Float = 1f,
    val showHint: Boolean = true,
) {
    companion object {
        /** Gallery ① Not started · no duration selected */
        val NotStarted = HomeUiState()

        /** Gallery ② 30 minutes selected · not running */
        val Selected30 = HomeUiState(
            centerLabel = "30:00",
            subtitle = "Locks automatically when time is up",
            finishAtLabel = "Ends at 23:45",
            canStart = true,
        )

        /** Gallery ③ Running */
        val Running = HomeUiState(
            running = true,
            centerLabel = "00:24:31",
            subtitle = "Locks automatically when time is up",
            progress = 0.817f,
            finishAtLabel = "Ends at 23:45",
            canReduce = true,
            primaryAction = PrimaryAction.PAUSE,
            canStart = true,
            showHint = false,
        )

        /** Gallery ④ Paused */
        val Paused = HomeUiState(
            running = true,
            paused = true,
            centerLabel = "00:24:31",
            subtitle = "Paused",
            progress = 0.817f,
            finishAtLabel = "Ends at 23:45",
            canReduce = true,
            primaryAction = PrimaryAction.RESUME,
            canStart = true,
            showHint = false,
        )

        /** Gallery ⑤ Dimming · dimProgress = 0.5 */
        val Dimming = HomeUiState(
            running = true,
            centerLabel = "00:00:30",
            subtitle = "Locks automatically when time is up",
            progress = 0.1f,
            finishAtLabel = "Ends at 23:45",
            canReduce = true,
            primaryAction = PrimaryAction.PAUSE,
            canStart = true,
            screenAlpha = 0.775f,
            showHint = false,
        )
    }
}
