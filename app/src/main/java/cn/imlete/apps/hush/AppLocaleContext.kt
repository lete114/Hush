package cn.imlete.apps.hush

import android.content.Context
import android.content.res.Configuration
import cn.imlete.apps.hush.data.SettingsStore
import cn.imlete.apps.hush.util.AppLocale

/**
 * Wraps the context according to the in-app language preference (spec 2026-10-06-i18n-design §4).
 *
 * Always wraps, one path for API 24–36, completely independent of the system language:
 * Application / Activity / accessibility service each fetch it once in attachBaseContext;
 * the countdown service fetches it every time it builds a notification (the next refresh after a language switch takes effect).
 */
fun Context.withAppLocale(): Context {
    val locale = AppLocale.localeOf(SettingsStore(this).appLocale)
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}
