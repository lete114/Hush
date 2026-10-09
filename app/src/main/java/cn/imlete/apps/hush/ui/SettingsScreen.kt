package cn.imlete.apps.hush.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.imlete.apps.hush.R
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.ui.components.IconChip
import cn.imlete.apps.hush.ui.theme.Amber
import cn.imlete.apps.hush.ui.theme.BodySize
import cn.imlete.apps.hush.ui.theme.HushTheme
import cn.imlete.apps.hush.ui.theme.Ink
import cn.imlete.apps.hush.ui.theme.Muted
import cn.imlete.apps.hush.ui.theme.OnInk
import cn.imlete.apps.hush.ui.theme.SmallSize
import cn.imlete.apps.hush.ui.theme.Surface
import cn.imlete.apps.hush.ui.theme.SurfaceHigh
import cn.imlete.apps.hush.ui.theme.TitleSize
import cn.imlete.apps.hush.ui.theme.Track
import cn.imlete.apps.hush.ui.theme.nightWash
import cn.imlete.apps.hush.util.AppLocale
import kotlin.math.roundToInt

/**
 * Settings page —— a port of SettingsActivity.kt:65-308.
 * The night background matches the home screen (theme.nightWash); UI_SPEC §3.7/§8.2-5's "no aurora" is a frozen historical state.
 */
@Composable
fun SettingsScreen(
    deviceAdmin: Boolean,
    notifications: Boolean,
    stepMinutes: Int,
    hideFromRecents: Boolean,
    pauseMedia: Boolean,
    batteryWhitelisted: Boolean,
    exactAlarmReady: Boolean,
    appLocale: String,
    onOpenLanguage: () -> Unit,
    onOpenDeviceAdmin: () -> Unit,
    onOpenNotifications: () -> Unit,
    onStepChange: (Int) -> Unit,
    onToggleHideRecents: (Boolean) -> Unit,
    onTogglePauseMedia: (Boolean) -> Unit,
    onOpenBattery: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // The header is fixed outside the scrolling container: the back key and title stay put while the content scrolls (spec 2026-10-05-pinned-subpage-header)
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                SettingsHeader(onBack)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            ) {
                Spacer(Modifier.height(28.dp))

                SettingsCard {
                    CardTitle(stringResource(R.string.settings_permissions))
                    CardGroupLabel(stringResource(R.string.settings_permissions_hint))
                    CapabilityRow(
                        label = stringResource(R.string.capability_device_admin),
                        granted = deviceAdmin,
                        icon = R.drawable.ic_shield,
                        onClick = onOpenDeviceAdmin,
                    )
                    CapabilityRow(
                        label = stringResource(R.string.capability_notifications),
                        granted = notifications,
                        icon = R.drawable.ic_notifications,
                        onClick = onOpenNotifications,
                    )
                }

                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    CardTitle(stringResource(R.string.settings_background))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconChip(R.drawable.ic_visibility_off)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_recents_hide), color = OnInk, fontSize = BodySize)
                            Text(
                                text = stringResource(R.string.setting_recents_hide_desc),
                                color = Muted,
                                fontSize = SmallSize,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        HushSwitch(checked = hideFromRecents, onClick = { onToggleHideRecents(!hideFromRecents) })
                    }

                    ProtectionRow(
                        icon = R.drawable.ic_battery,
                        label = stringResource(R.string.setting_battery),
                        subtitle = stringResource(R.string.setting_battery_desc),
                        ok = batteryWhitelisted,
                        okStatus = stringResource(R.string.setting_battery_on),
                        pendingStatus = stringResource(R.string.setting_battery_off),
                        onClick = onOpenBattery,
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ProtectionRow(
                            icon = R.drawable.ic_schedule,
                            label = stringResource(R.string.setting_exact),
                            subtitle = stringResource(R.string.setting_exact_desc),
                            ok = exactAlarmReady,
                            okStatus = stringResource(R.string.capability_on),
                            pendingStatus = stringResource(R.string.capability_off),
                            onClick = onOpenExactAlarm,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    CardTitle(stringResource(R.string.settings_pause_media_title))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconChip(R.drawable.ic_music_off)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.setting_pause_media), color = OnInk, fontSize = BodySize)
                            Text(
                                text = stringResource(R.string.setting_pause_media_desc),
                                color = Muted,
                                fontSize = SmallSize,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        HushSwitch(checked = pauseMedia, onClick = { onTogglePauseMedia(!pauseMedia) })
                    }
                }

                Spacer(Modifier.height(16.dp))
                SettingsCard {
                    CardTitle(stringResource(R.string.settings_step_title))
                    Text(
                        text = stringResource(R.string.settings_step_desc),
                        color = Muted,
                        fontSize = SmallSize,
                        lineHeight = 20.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_step_value, stepMinutes),
                            color = Amber,
                            fontSize = BodySize,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_step_range,
                                SettingsStore.MIN_STEP_MINUTES,
                                SettingsStore.MAX_STEP_MINUTES,
                            ),
                            color = Muted,
                            fontSize = SmallSize,
                        )
                    }
                    Slider(
                        value = stepMinutes.toFloat(),
                        onValueChange = { onStepChange(it.roundToInt()) },
                        valueRange = SettingsStore.MIN_STEP_MINUTES.toFloat()..SettingsStore.MAX_STEP_MINUTES.toFloat(),
                        steps = SettingsStore.MAX_STEP_MINUTES - SettingsStore.MIN_STEP_MINUTES - 1, // Whole-step count minus the two endpoints (1..30 → 28)
                        colors = SliderDefaults.colors(
                            thumbColor = Amber,
                            activeTrackColor = Amber,
                            inactiveTrackColor = Track,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))

                SettingsCard {
                    CardTitle(stringResource(R.string.settings_general_title))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLanguage).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconChip(R.drawable.ic_language)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.settings_language),
                            color = OnInk,
                            fontSize = BodySize,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(if (appLocale == AppLocale.TAG_ZH) R.string.language_zh else R.string.language_en),
                            color = Muted,
                            fontSize = SmallSize,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = stringResource(R.string.desc_enter),
                            tint = Muted,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconChip(R.drawable.ic_info)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.label_about), color = OnInk, fontSize = BodySize, modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_forward),
                            contentDescription = stringResource(R.string.desc_enter),
                            tint = Muted,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.settings_title), color = OnInk, fontSize = TitleSize, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.action_back),
                tint = Muted,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        color = OnInk,
        fontSize = TitleSize,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

/** Group subheading: gray group names inside the permissions card such as "lock method · enable any one". */
@Composable
private fun CardGroupLabel(text: String) {
    Text(
        text = text,
        color = Muted,
        fontSize = SmallSize,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

/**
 * Capability row (single-line style, spec 2026-10-05-recents-hide plan A · revision):
 * left icon chip (replacing the original ✓/○ status symbols, spec revision 10-A) + label, with status text in a status color on the right (enabled = amber).
 */
@Composable
private fun CapabilityRow(
    label: String,
    granted: Boolean,
    icon: Int,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon)
        Spacer(Modifier.width(10.dp))
        Text(label, color = OnInk, fontSize = BodySize, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(if (granted) R.string.capability_on else R.string.capability_off),
            color = if (granted) Amber else Muted,
            fontSize = SmallSize,
        )
    }
}

/**
 * Protection row (spec 2026-10-05-reliability-guardrails): icon chip + two-line text
 * (label OnInk Body / subtitle Muted Small) + status on the right
 * (satisfied = amber status word / not satisfied = gray action word), the whole row clickable.
 */
@Composable
private fun ProtectionRow(
    icon: Int,
    label: String,
    subtitle: String,
    ok: Boolean,
    okStatus: String,
    pendingStatus: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconChip(icon)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = OnInk, fontSize = BodySize)
            Text(
                text = subtitle,
                color = Muted,
                fontSize = SmallSize,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (ok) okStatus else pendingStatus,
            color = if (ok) Amber else Muted,
            fontSize = SmallSize,
        )
    }
}

/** M3 Switch: track 52×32, thumb 24dp selected / 16dp unselected. The whole block is clickable when [onClick] is non-null (display-only rows pass null). */
@Composable
private fun HushSwitch(checked: Boolean, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) SurfaceHigh else Track)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        if (checked) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .size(24.dp)
                    .background(Amber, CircleShape),
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
                    .size(16.dp)
                    .background(Muted, CircleShape),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101A, widthDp = 417, heightDp = 929)
@Composable
private fun SettingsScreenPreview() {
    HushTheme {
        Box(Modifier.fillMaxSize().background(Ink).nightWash()) {
            SettingsScreen(
                deviceAdmin = true,
                notifications = true,
                stepMinutes = SettingsStore.DEFAULT_STEP_MINUTES,
                hideFromRecents = true,
                pauseMedia = false,
                batteryWhitelisted = true,
                exactAlarmReady = true,
                appLocale = AppLocale.TAG_EN,
                onOpenLanguage = {},
                onOpenDeviceAdmin = {},
                onOpenNotifications = {},
                onStepChange = {},
                onToggleHideRecents = {},
                onTogglePauseMedia = {},
                onOpenBattery = {},
                onOpenExactAlarm = {},
                onOpenAbout = {},
                onBack = {},
            )
        }
    }
}
