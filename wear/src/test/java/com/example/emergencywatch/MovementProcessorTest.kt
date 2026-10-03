package com.example.emergencywatch

import com.example.emergencywatch.domain.sensor.MovementProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementProcessorTest {

    @Test
    fun testStillnessCalculation() {
        val processor = MovementProcessor()
        // First read initializes baseline
        processor.processAccelerometer(0f, 0f, 9.81f)

        // Subsequent tiny movement
        val movement = processor.processAccelerometer(0.01f, 0.01f, 9.81f)
        assertTrue(movement in 0.0..5.0)
    }

    @Test
    fun testFallImpactDetection() {
        val processor = MovementProcessor()

        // Normal g-force (gravity ~ 9.81)
        val (normalFall, _) = processor.checkFallImpact(0f, 0f, 9.81f)
        assertFalse(normalFall)

        // High acceleration spike (30 m/s² ~ 3.0g)
        val (impactFall, confidence) = processor.checkFallImpact(20f, 20f, 15f)
        assertTrue(impactFall)
        assertTrue(confidence != null && confidence in 0.5..1.0)
    }
}
