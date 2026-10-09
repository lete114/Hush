package cn.imlete.apps.hush

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.lock.DeviceAdminLock
import cn.imlete.apps.hush.service.CountdownService
import cn.imlete.apps.hush.timer.Choice
import cn.imlete.apps.hush.ui.AboutScreen
import cn.imlete.apps.hush.ui.CustomDurationSheet
import cn.imlete.apps.hush.ui.DurationSheet
import cn.imlete.apps.hush.ui.HomeScreen
import cn.imlete.apps.hush.ui.HomeViewModel
import cn.imlete.apps.hush.ui.LanguageSheet
import cn.imlete.apps.hush.ui.PrimaryAction
import cn.imlete.apps.hush.ui.Screen
import cn.imlete.apps.hush.ui.SettingsScreen
import cn.imlete.apps.hush.ui.TICK_MS
import cn.imlete.apps.hush.ui.components.HushActionDialog
import cn.imlete.apps.hush.ui.isForward
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.Ink
import cn.imlete.apps.hush.ui.theme.nightWash
import cn.imlete.apps.hush.util.DurationFormatter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    /** Notification-tap signal (spec §4): incremented on onNewIntent if an existing instance receives it; HushApp observes it to return to the home screen. */
    val openFromNotification = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark (HushTheme ignores system dark mode), so the default auto() style —
        // which follows the system theme and turns the icons black in light system mode — would be
        // unreadable on the Ink background. Force light icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        applyRecentsVisibility(this) // D: hide the recents card according to the setting (spec 2026-10-05-recents-hide)
        setContent {
            HushTheme {
                HushApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == CountdownService.ACTION_OPEN_NOTIFICATION) {
            openFromNotification.intValue++
        }
    }
}

private enum class Overlay { NONE, DURATION_SHEET, CUSTOM_WHEEL, LANGUAGE_SHEET }

/**
 * Home screen / settings page + overlays. The timing and lock-screen capability are driven by [HomeViewModel];
 * this layer only handles the clock tick, forwards commands to the ViewModel, and renders the guidance dialog.
 */
@Composable
private fun HushApp() {
    val context = LocalContext.current
    val viewModel = remember { HomeViewModel(context.applicationContext) }

    // screen uses rememberSaveable: after a language switch triggers activity.recreate(), the user must stay
    // on the settings page; plain remember would reset back to home (spec §5, the original "land on home" decision revised after review).
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var overlay by remember { mutableStateOf(Overlay.NONE) }

    val settingsStore = remember { SettingsStore(context.applicationContext) }
    var hideFromRecents by remember { mutableStateOf(settingsStore.hideFromRecents) }
    var pauseMedia by remember { mutableStateOf(settingsStore.pauseMediaOnLock) }

    val activity = LocalContext.current as MainActivity
    // D fallback: onCreate may run before the task is fully established (or must take effect again after a process
    // restart); it is idempotent, so repeated calls are harmless.
    LaunchedEffect(Unit) { applyRecentsVisibility(activity) }
    // Notification tap → return to the home screen and close the overlay (spec §4 "land on home screen"; the first-frame value of 0 does not trigger).
    LaunchedEffect(activity.openFromNotification.intValue) {
        if (activity.openFromNotification.intValue > 0) {
            screen = Screen.HOME
            overlay = Overlay.NONE
        }
    }

    // First-launch notification permission request. It must remain at the top level of HushApp (it must not be moved
    // into a when branch), otherwise leaving the composition loses the launcher's remembered state and the callback breaks.
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.showLockDialogIfNotReady() } // The permission box has been answered (a denial does not block) → proceed to the capability check, ensuring "permission first, guidance second"

    // Entry prompt flow (spec §1, decisions A/A1/a): 13+ not granted → system permission box first (its callback proceeds to the capability check);
    // granted or Android ≤12 → capability check directly. LaunchedEffect(Unit) runs once per composition = once per cold start,
    // and is not repeated on warm resume (returning, coming back from settings) → no new proactive popup.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.showLockDialogIfNotReady()
        }
    }

    // Layered system back: overlay → guidance dialog → about page → settings page → home (exit).
    // When every layer is empty, enabled = false → not consumed → the system default finishes the app (design §4).
    // The dialog is not a Compose Dialog (Overlays.kt's DialogContainer is a self-drawn Box), so it has no built-in back-to-close,
    // therefore this single handler is the only consumer; there is no double consumption.
    BackHandler(
        enabled = overlay != Overlay.NONE || viewModel.showLockDialog ||
            screen == Screen.SETTINGS || screen == Screen.ABOUT,
    ) {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            viewModel.showLockDialog -> viewModel.dismissLockDialog()
            screen == Screen.ABOUT -> screen = Screen.SETTINGS
            else -> screen = Screen.HOME
        }
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var nowWall by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            // Dependency-free resume refresh: re-read the persisted state every tick so results written by the
            // receiver on time-up/failure take effect in the UI (no need for lifecycle-runtime-compose's LifecycleEventEffect).
            viewModel.refresh()
            now = SystemClock.elapsedRealtime()
            nowWall = System.currentTimeMillis()
        }
    }

    val state = viewModel.uiState(now, nowWall)
    val openDeviceAdmin: () -> Unit = {
        try {
            context.startActivity(deviceAdminIntent(context))
        } catch (_: Exception) {
            // Some ROMs have no device-admin activation page (ActivityNotFoundException), others throw SecurityException;
            // fall back level by level: security settings (the page containing the device-management entry) → app details (design §7 revision, spec 2026-10-07-remove-accessibility-lock §3:
            // the original fallback target was accessibility settings, which was removed along with the accessibility path). The fallback itself is fault-tolerant; a double failure does not crash.
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (_: Exception) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}")),
                    )
                }
            }
        }
    }

    // Reliability status row (spec 2026-10-05-reliability-guardrails): intents fall back level by level, compatible with various ROMs.
    val openBattery: () -> Unit = {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (_: Exception) {
            // Some ROMs lack a direct confirmation box (ActivityNotFoundException/SecurityException) → battery-optimization list → app details fallback.
            try {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.parse("package:${context.packageName}")),
                    )
                }
            }
        }
    }
    val openExactAlarm: () -> Unit = {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:${context.packageName}")),
            )
        } catch (_: Exception) {
            // The direct page is unavailable → app details fallback; the fallback itself is also fault-tolerant, a double failure does not crash.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}")),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Ink).nightWash()) {
        AnimatedContent(
            targetState = screen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val forward = isForward(initialState, targetState)
                val inX: (Int) -> Int = { if (forward) it / 3 else -it / 3 }
                (
                    slideInHorizontally(tween(250, easing = FastOutSlowInEasing)) { inX(it) } +
                        fadeIn(tween(250, easing = FastOutSlowInEasing))
                ) togetherWith (
                    slideOutHorizontally(tween(250, easing = FastOutSlowInEasing)) { -inX(it) } +
                        fadeOut(tween(250, easing = FastOutSlowInEasing))
                )
            },
        ) { s ->
            when (s) {
                Screen.HOME -> HomeScreen(
                    state = state,
                    onOpenDurationSheet = { overlay = Overlay.DURATION_SHEET },
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onPrimary = {
                        when (state.primaryAction) {
                            PrimaryAction.START ->
                                if (state.canStart) {
                                    // The notification permission request has been moved to the entry point (spec §1 rule 6); if not granted, it starts as usual, just without a notification
                                    viewModel.start(now, nowWall)
                                } else {
                                    // No valid duration (Off / 00:00): tapping the primary key instead opens the duration drawer,
                                    // never silently starting with a 30-minute placeholder (spec 2026-10-05-idle-start-opens-drawer §3)
                                    overlay = Overlay.DURATION_SHEET
                                }
                            PrimaryAction.PAUSE -> viewModel.pause(now, nowWall)
                            PrimaryAction.RESUME -> viewModel.resume(now, nowWall)
                        }
                    },
                    onReduce = { viewModel.reduce(now) },
                    onExtend = { viewModel.extend(now) },
                )

                Screen.SETTINGS -> SettingsScreen(
                    deviceAdmin = DeviceAdminLock.isActive(context),
                    notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                    stepMinutes = viewModel.stepMinutes,
                    hideFromRecents = hideFromRecents,
                    pauseMedia = pauseMedia,
                    batteryWhitelisted = viewModel.batteryWhitelisted,
                    exactAlarmReady = viewModel.exactAlarmReady,
                    appLocale = settingsStore.appLocale,
                    onOpenLanguage = { overlay = Overlay.LANGUAGE_SHEET },
                    onStepChange = { viewModel.setStep(it) },
                    onToggleHideRecents = { v ->
                        settingsStore.hideFromRecents = v
                        hideFromRecents = v
                        applyRecentsVisibility(activity) // Takes effect immediately: the recents card appears/disappears right away
                    },
                    onTogglePauseMedia = { v ->
                        settingsStore.pauseMediaOnLock = v
                        pauseMedia = v
                    },
                    onOpenDeviceAdmin = openDeviceAdmin,
                    onOpenNotifications = {
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra("app_uid", context.applicationInfo.uid), // API 24–25 compatibility (the EXTRA_APP_PACKAGE constant is 26+, its value is also "app_package")
                            )
                        } catch (_: Exception) {
                            // Some ROMs have no standalone notification settings page (ActivityNotFoundException), others throw
                            // SecurityException; fall back to app details settings (on API 24–25 the notification toggle is on that page).
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .setData(Uri.parse("package:${context.packageName}")),
                            )
                        }
                    },
                    onOpenBattery = openBattery,
                    onOpenExactAlarm = openExactAlarm,
                    onBack = { screen = Screen.HOME },
                    onOpenAbout = { screen = Screen.ABOUT },
                )

                Screen.ABOUT -> AboutScreen(
                    onBack = { screen = Screen.SETTINGS },
                )
            }
        }

        // The two drawers stay permanently composed and are driven by visible: only while the content remains
        // in the composition after the state flips can the exit animation play (spec §3.3)
        DurationSheet(
            visible = overlay == Overlay.DURATION_SHEET,
            choice = viewModel.timerState.choice,
            onDismiss = { overlay = Overlay.NONE },
            onSelect = {
                viewModel.select(it, now, nowWall)
                overlay = Overlay.NONE
            },
            onCustom = { overlay = Overlay.CUSTOM_WHEEL },
        )

        // Only when custom is currently selected is its duration used as the wheel's initial value; preset selected/not enabled → 0/0, opening shows 00:00 (spec §① 2026-10-05 revision)
        val savedCustomMs = (viewModel.timerState.choice as? Choice.Custom)?.durationMs ?: 0L
        CustomDurationSheet(
            visible = overlay == Overlay.CUSTOM_WHEEL,
            initialHours = (savedCustomMs / 3_600_000L).toInt().coerceIn(0, 23),
            initialMinutes = ((savedCustomMs % 3_600_000L) / 60_000L).toInt().coerceIn(0, 59),
            onDismiss = { overlay = Overlay.NONE },
            onConfirm = { hours, minutes ->
                val total = (hours * 3600000L + minutes * 60000L)
                    .coerceAtMost(DurationFormatter.MAX_DURATION_MS)
                viewModel.select(Choice.Custom(total), now, nowWall)
                overlay = Overlay.NONE
            },
        )

        LanguageSheet(
            visible = overlay == Overlay.LANGUAGE_SHEET,
            current = settingsStore.appLocale,
            onDismiss = { overlay = Overlay.NONE },
            onSelect = { tag ->
                overlay = Overlay.NONE
                settingsStore.appLocale = tag
                CountdownService.syncIfRunning(context) // Service running → the notification and channel switch language immediately
                activity.recreate() // attachBaseContext wraps again → the whole app switches language; timer state is restored from the store (an in-process recreate does not trigger cold-start discarding)
            },
        )

        // Plan A: this box is shown only when start() runs and device admin is unavailable (the accessibility entry was removed, spec 2026-10-07-remove-accessibility-lock §5).
        if (viewModel.showLockDialog) {
            HushActionDialog(
                title = stringResource(R.string.lock_not_ready_title),
                text = stringResource(R.string.lock_not_ready_msg),
                primaryLabel = stringResource(R.string.action_open_device_admin),
                onPrimary = {
                    viewModel.dismissLockDialog()
                    openDeviceAdmin()
                },
                dismissLabel = stringResource(R.string.action_cancel),
                onDismiss = { viewModel.dismissLockDialog() },
            )
        }
    }
}

/**
 * D (spec 2026-10-05-recents-hide): hides/shows this task in the recents list according to the setting.
 * Uses the runtime `AppTask.setExcludeFromRecents` instead of the manifest static `excludeFromRecents` —
 * once a static attribute is declared it cannot be turned back off by the toggle. The task must re-apply on every rebuild, hence the idempotent calls at several spots.
 */
private fun applyRecentsVisibility(activity: Activity) {
    val hide = SettingsStore(activity).hideFromRecents
    try {
        activity.getSystemService(ActivityManager::class.java)
            .appTasks
            .firstOrNull()
            ?.setExcludeFromRecents(hide)
    } catch (_: Exception) {
        // A few ROMs throw SecurityException / have no such task — a failed hide only affects the chance of accidental kills, not worth crashing over
    }
}

/**
 * Device-admin activation page. `EXTRA_ADD_EXPLANATION` must be included: the system page only lists the policy name (force-lock),
 * and without an explanation of the reason the user faces the authorization box with no way to judge (spec 2026-10-07 §1).
 */
private fun deviceAdminIntent(context: Context): Intent =
    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, DeviceAdminLock.component(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.device_admin_explanation),
        )
