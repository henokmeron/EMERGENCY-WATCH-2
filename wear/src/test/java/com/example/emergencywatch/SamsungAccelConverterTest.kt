package com.example.emergencywatch

import com.example.emergencywatch.domain.sensor.SamsungAccelConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungAccelConverterTest {

    @Test
    fun zeroRawIsZeroMs2() {
        assertEquals(0f, SamsungAccelConverter.toMetersPerSecondSquared(0), 0.0001f)
    }

    @Test
    fun positiveRawConvertsWithSamsungScale() {
        // Scale factor from Samsung docs: 9.81 / (16383.75 / 4.0)
        val expectedScale = 9.81f / (16383.75f / 4.0f)
        val raw = 4096
        val ms2 = SamsungAccelConverter.toMetersPerSecondSquared(raw)
        assertEquals(expectedScale * raw, ms2, 0.0001f)
        assertTrue(ms2 > 0f)
    }

    @Test
    fun negativeRawPreservesSign() {
        val ms2 = SamsungAccelConverter.toMetersPerSecondSquared(-1000)
        assertTrue(ms2 < 0f)
    }
}
