package com.nopeek.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartMathTest {

    @Test
    fun fractionNormal() = assertEquals(0.6f, fractionOf(18f, 30f), 0.0001f)

    @Test
    fun fractionClampsAtOne() = assertEquals(1f, fractionOf(45f, 30f), 0f)

    @Test
    fun fractionZeroMaxOrNegativeInputs() {
        assertEquals(0f, fractionOf(10f, 0f), 0f); assertEquals(0f, fractionOf(-1f, 30f), 0f)
    }

    @Test
    fun sweepDegrees() {
        assertEquals(216f, sweepDegrees(0.6f), 0.0001f)
        assertEquals(0f, sweepDegrees(-5f), 0f); assertEquals(360f, sweepDegrees(2f), 0f)
    }
}
