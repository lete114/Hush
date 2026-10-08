package cn.imlete.apps.hush.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GaugeGeometryTest {

    @Test
    fun `tick angles span both endpoints`() {
        val angles = gaugeTickAnglesDeg()
        assertEquals(11, angles.size)
        assertEquals(135f, angles.first(), 0.001f)
        assertEquals(405f, angles.last(), 0.001f)
    }

    @Test
    fun `tick angles are evenly spaced`() {
        val angles = gaugeTickAnglesDeg()
        val step = angles[1] - angles[0]
        assertEquals(27f, step, 0.001f)
        for (i in 1 until angles.size) {
            assertEquals(step, angles[i] - angles[i - 1], 0.001f)
        }
    }

    @Test
    fun `progress sweep maps endpoints and midpoint`() {
        assertEquals(0f, gaugeProgressSweepDeg(0f), 0.001f)
        assertEquals(270f, gaugeProgressSweepDeg(1f), 0.001f)
        assertEquals(135f, gaugeProgressSweepDeg(0.5f), 0.001f)
    }

    @Test
    fun `progress sweep clamps out of range`() {
        assertEquals(0f, gaugeProgressSweepDeg(-0.5f), 0.001f)
        assertEquals(270f, gaugeProgressSweepDeg(2f), 0.001f)
    }

    @Test
    fun `progress sweep of NaN is zero`() {
        assertEquals(0f, gaugeProgressSweepDeg(Float.NaN), 0.001f)
    }

    @Test
    fun `tick radii derive from arc inner edge`() {
        val (outer, inner) = gaugeTickRadiiPx(radiusPx = 123f, strokePx = 14f, gapPx = 4f, lengthPx = 10f)
        assertEquals(112f, outer, 0.001f)
        assertEquals(102f, inner, 0.001f)
    }

    @Test
    fun `tick inner radius floors at zero`() {
        val (_, inner) = gaugeTickRadiiPx(radiusPx = 20f, strokePx = 40f, gapPx = 4f, lengthPx = 10f)
        assertEquals(0f, inner, 0.001f)
    }
}
