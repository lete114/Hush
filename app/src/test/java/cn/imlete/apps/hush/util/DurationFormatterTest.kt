package cn.imlete.apps.hush.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

class DurationFormatterTest {
    /** formatClock assertions depend on local time zone — pinned to Asia/Shanghai (UTC+8, no DST) so they are independent of machine TZ. */
    private lateinit var previousDefault: TimeZone

    @Before
    fun pinTimeZone() {
        previousDefault = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousDefault)
    }

    @Test fun `formatPreset 0`() = assertEquals("00:00", DurationFormatter.formatPreset(0))
    @Test fun `formatPreset 45min`() = assertEquals("45:00", DurationFormatter.formatPreset(45 * 60000))
    @Test fun `formatPreset 60min no hour prefix`() = assertEquals("60:00", DurationFormatter.formatPreset(60 * 60000))
    @Test fun `formatPreset 90min`() = assertEquals("90:00", DurationFormatter.formatPreset(90 * 60000))
    @Test fun `formatCompact 45min`() = assertEquals("45:00", DurationFormatter.formatCompact(45 * 60000))
    @Test fun `formatCompact 90min with hour prefix`() = assertEquals("01:30:00", DurationFormatter.formatCompact(90 * 60000))
    @Test fun `format 0`() = assertEquals("00:00:00", DurationFormatter.format(0))
    @Test fun `format negative clamped to 0`() = assertEquals("00:00:00", DurationFormatter.format(-1))
    @Test fun `format 59s`() = assertEquals("00:00:59", DurationFormatter.format(59000))
    @Test fun `format 89m45s`() = assertEquals("01:29:45", DurationFormatter.format(89 * 60000 + 45000))
    @Test fun `format max`() = assertEquals("23:59:00", DurationFormatter.format(DurationFormatter.MAX_DURATION_MS))
    // ---- formatClock: outputs local wall clock HH:mm (epoch is wall-clock domain ms; TZ fixed to Asia/Shanghai UTC+8) ----
    @Test fun `formatClock local 3am`() =
        assertEquals("03:00", DurationFormatter.formatClock(19 * 3600000)) // UTC 19:00 -> Shanghai next day 03:00
    @Test fun `formatClock local 2340`() =
        assertEquals("23:40", DurationFormatter.formatClock(15 * 3600000 + 40 * 60000)) // UTC 15:40 -> Shanghai 23:40
    @Test fun `formatClock crossing midnight`() =
        assertEquals("00:10", DurationFormatter.formatClock(16 * 3600000 + 10 * 60000)) // Shanghai 23:40 + 30min -> next day 00:10
    @Test fun `formatClock noon`() =
        assertEquals("12:00", DurationFormatter.formatClock(4 * 3600000)) // UTC 04:00 -> Shanghai 12:00
    @Test fun `formatClock UTC midnight to Shanghai 08`() =
        assertEquals("08:00", DurationFormatter.formatClock(0)) // UTC 1970-01-01 00:00 -> Shanghai 08:00
    @Test fun `MAX constant`() = assertEquals(86_340_000L, DurationFormatter.MAX_DURATION_MS)

    // ---- formatCountdown: only for custom duration with total > 60 minutes use HH:MM:SS ----
    @Test fun `formatCountdown custom 90min remaining 89m45s`() =
        assertEquals("01:29:45", DurationFormatter.formatCountdown(true, 90 * 60000, 89 * 60000 + 45000))
    @Test fun `formatCountdown custom max`() =
        assertEquals(
            "23:59:00",
            DurationFormatter.formatCountdown(true, DurationFormatter.MAX_DURATION_MS, DurationFormatter.MAX_DURATION_MS),
        )
    @Test fun `formatCountdown custom 61min remaining 60m59s`() =
        assertEquals("01:00:59", DurationFormatter.formatCountdown(true, 61 * 60000, 60 * 60000 + 59000))
    @Test fun `formatCountdown custom 90min at zero`() =
        assertEquals("00:00:00", DurationFormatter.formatCountdown(true, 90 * 60000, 0))
    @Test fun `formatCountdown custom exactly 60min uses MMSS`() =
        assertEquals("60:00", DurationFormatter.formatCountdown(true, 60 * 60000, 60 * 60000))
    @Test fun `formatCountdown custom 60min remaining 59m59s`() =
        assertEquals("59:59", DurationFormatter.formatCountdown(true, 60 * 60000, 59 * 60000 + 59000))
    @Test fun `formatCountdown custom 30min remaining 29min`() =
        assertEquals("29:00", DurationFormatter.formatCountdown(true, 30 * 60000, 29 * 60000))
    @Test fun `formatCountdown custom 10min remaining 59s`() =
        assertEquals("00:59", DurationFormatter.formatCountdown(true, 10 * 60000, 59000))
    @Test fun `formatCountdown preset 60min always MMSS`() =
        assertEquals("60:00", DurationFormatter.formatCountdown(false, 60 * 60000, 60 * 60000))
    @Test fun `formatCountdown preset extended to 65min still MMSS`() =
        assertEquals("64:59", DurationFormatter.formatCountdown(false, 65 * 60000, 64 * 60000 + 59000))
    @Test fun `formatCountdown preset 30min`() =
        assertEquals("30:00", DurationFormatter.formatCountdown(false, 30 * 60000, 30 * 60000))
    @Test fun `formatMinutes 60min no hour prefix`() = assertEquals("60:00", DurationFormatter.formatMinutes(60 * 60000))
    @Test fun `formatMinutes 0`() = assertEquals("00:00", DurationFormatter.formatMinutes(0))
    @Test fun `formatMinutes negative clamped to 0`() = assertEquals("00:00", DurationFormatter.formatMinutes(-1))
    // ---- negative boundaries: total/value clamped to 0; format branch determined by clamped total ----
    @Test fun `formatCountdown negative total clamped to 0 goes to MMSS branch`() =
        assertEquals("00:00", DurationFormatter.formatCountdown(true, -1, -1))
    @Test fun `formatCountdown negative value clamped to 0`() =
        assertEquals("00:00:00", DurationFormatter.formatCountdown(true, 2 * 3600_000, -1))
    // ---- formatClock negative epoch: after TZ offset floorDiv/mod gives valid HH:mm ----
    @Test fun `formatClock negative epoch produces HHmm`() =
        assertEquals("07:59", DurationFormatter.formatClock(-1)) // UTC 1969-12-31 23:59:59.999 -> Shanghai 1970-01-01 07:59:59.999
}
