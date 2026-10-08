package cn.imlete.apps.hush.util

import java.util.Locale

/**
 * Pure UI-language logic (spec 2026-10-06-i18n-design §4):
 * recognizes only `en` / `zh`; everything else (null, corrupt, unknown tags) is normalized to `en` —
 * defaults to English, completely independent of the system language. Imports no `android.*`, guaranteeing JVM testability.
 */
object AppLocale {

    const val TAG_EN = "en"
    const val TAG_ZH = "zh"

    /** Normalize a stored value → language tag; invalid input never throws, falling back to [TAG_EN]. */
    fun resolveLocaleTag(raw: String?): String = when (raw) {
        TAG_EN -> TAG_EN
        TAG_ZH -> TAG_ZH
        else -> TAG_EN
    }

    /** Tag → the Locale used for resource matching. zh carries the CN region code to hit `values-zh-rCN`. */
    fun localeOf(tag: String): Locale = when (resolveLocaleTag(tag)) {
        TAG_ZH -> Locale.SIMPLIFIED_CHINESE
        else -> Locale.ENGLISH
    }
}
