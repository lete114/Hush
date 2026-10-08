package cn.imlete.apps.hush.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceTimerMathTest {
    private val STEP = ServiceTimerMath.STEP_MS
    private val MAX = ServiceTimerMath.MAX_DURATION_MS

    @Test fun `start initial remaining equals total`() = assertEquals(1800000, ServiceTimerMath.remainingAt(1800000, 1000, 1000))
    @Test fun `start remaining after 10s`() = assertEquals(1790000, ServiceTimerMath.remainingAt(1800000, 1000, 11000))

    @Test fun `extend endAt equals now plus new remaining`() {
        val (newTotal, newRemaining) = ServiceTimerMath.extend(1800000, 1740000, STEP)
        assertEquals(1740000 + 300000, newRemaining)
        assertEquals(1800000 + 300000, newTotal)
    }
    @Test fun `extend elapsed time unchanged`() {
        val total = 1800000L; val left = 1740000L
        val (newTotal, newRemaining) = ServiceTimerMath.extend(total, left, STEP)
        assertEquals(total - left, newTotal - newRemaining)
    }
    @Test fun `extend remaining does not exceed total`() {
        val (_, newRemaining) = ServiceTimerMath.extend(1800000, 1740000, STEP)
        assertTrue(newRemaining <= 1800000 + 300000)
    }
    @Test fun `extend at limit adds only the remainder`() {
        val total = MAX - 10000
        val (newTotal, _) = ServiceTimerMath.extend(total, total - 60000, STEP)
        assertEquals(10000, newTotal - total)
        assertEquals(MAX, newTotal)
    }
    @Test fun `extend with custom step 1 minute`() {
        val (newTotal, newRemaining) = ServiceTimerMath.extend(1800000, 1740000, 60_000L)
        assertEquals(1740000 + 60_000, newRemaining)
        assertEquals(1800000 + 60_000, newTotal)
    }
    @Test fun `extend when step exceeds limit remainder adds only remainder`() {
        val total = MAX - 5_000
        val (newTotal, _) = ServiceTimerMath.extend(total, total - 60_000, 60_000L)
        assertEquals(MAX, newTotal)
    }

    @Test fun `reduce remaining 4min unchanged`() = assertEquals(0, ServiceTimerMath.reduce(4 * 60000, STEP))
    @Test fun `reduce remaining 5min exactly one step unchanged`() = assertEquals(0, ServiceTimerMath.reduce(5 * 60000, STEP))
    @Test fun `reduce remaining 6min reduces by 1min`() = assertEquals(60000, ServiceTimerMath.reduce(6 * 60000, STEP))
    @Test fun `reduce remaining 11min reduces by 5min`() = assertEquals(300000, ServiceTimerMath.reduce(11 * 60000, STEP))
    @Test fun `reduce remaining 30min reduces by 5min`() = assertEquals(300000, ServiceTimerMath.reduce(30 * 60000, STEP))
    @Test fun `reduce remaining 2min unchanged`() = assertEquals(0, ServiceTimerMath.reduce(2 * 60000, STEP))
    @Test fun `reduce remaining 0 unchanged`() = assertEquals(0, ServiceTimerMath.reduce(0, STEP))
    @Test fun `reduce remainder after reduction is at least one step`() {
        val applied = ServiceTimerMath.reduce(6 * 60000, STEP)
        assertTrue(6 * 60000 - applied >= STEP)
    }
    @Test fun `reduce with custom step 30 minutes`() =
        assertEquals(1_800_000L, ServiceTimerMath.reduce(3_600_000L, 1_800_000L))
    @Test fun `reduce when step greater than remaining unchanged`() =
        assertEquals(0L, ServiceTimerMath.reduce(60_000L, 1_800_000L))
    @Test fun `reduce with custom step 1 minute on 2 minutes reduces by 1 minute`() =
        assertEquals(60_000L, ServiceTimerMath.reduce(120_000L, 60_000L))

    @Test fun `MAX constant matches DurationFormatter`() =
        assertEquals(cn.imlete.apps.hush.util.DurationFormatter.MAX_DURATION_MS, ServiceTimerMath.MAX_DURATION_MS)
}
