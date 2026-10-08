package cn.imlete.apps.hush.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** spec 2026-10-06-i18n-design §4: only accept en/zh; all others (null, malformed, unknown) normalize to en. */
class AppLocaleTest {

    @Test fun `en passes through`() = assertEquals("en", AppLocale.resolveLocaleTag("en"))

    @Test fun `zh passes through`() = assertEquals("zh", AppLocale.resolveLocaleTag("zh"))

    @Test fun `null normalizes to en`() = assertEquals("en", AppLocale.resolveLocaleTag(null))

    @Test fun `unknown tag normalizes to en`() {
        assertEquals("en", AppLocale.resolveLocaleTag("ja"))
        assertEquals("en", AppLocale.resolveLocaleTag("zh-CN"))
        assertEquals("en", AppLocale.resolveLocaleTag(""))
    }

    /** zh must include CN region code to match `values-zh-rCN` (language-only does not guarantee matching region-specific resources). */
    @Test fun `zh maps to Simplified Chinese region code`() =
        assertEquals(Locale.SIMPLIFIED_CHINESE, AppLocale.localeOf("zh"))

    @Test fun `en maps to English`() = assertEquals(Locale.ENGLISH, AppLocale.localeOf("en"))

    @Test fun `localeOf invalid input normalizes to English`() = assertEquals(Locale.ENGLISH, AppLocale.localeOf("xx"))
}
