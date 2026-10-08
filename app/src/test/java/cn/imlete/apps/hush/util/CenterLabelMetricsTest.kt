package cn.imlete.apps.hush.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CenterLabelMetricsTest {
    private fun assertMetrics(text: String, scale: Float, trackingSp: Float) {
        val m = centerLabelMetrics(text)
        assertEquals("scale for '$text'", scale, m.scale, 0.0001f)
        assertEquals("trackingSp for '$text'", trackingSp, m.trackingSp, 0.0001f)
    }

    @Test fun `5 char MMSS no scaling down`() = assertMetrics("30:00", 1f, 2f)
    @Test fun `empty string treated as short text`() = assertMetrics("", 1f, 2f)
    @Test fun `6 char scales down`() = assertMetrics("100:00", 0.8f, 0f)
    @Test fun `7 char scales down`() = assertMetrics("1439:59", 0.8f, 0f)
    @Test fun `8 char scales down`() = assertMetrics("01:24:00", 0.8f, 0f)
}
