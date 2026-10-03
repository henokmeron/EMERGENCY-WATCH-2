package com.example.emergencywatch

import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.remote.model.EventRequest
import com.example.emergencywatch.data.remote.model.PairResponse
import com.example.emergencywatch.data.remote.model.ReadingDto
import com.example.emergencywatch.data.remote.model.TelemetryRequest
import com.example.emergencywatch.data.remote.model.TelemetryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiContractTest {

    @Test
    fun testPairResponseParsing() {
        val json = """
            {
                "status": "success",
                "device_id": "11111111-2222-3333-4444-555555555555",
                "patient_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "device_token": "sdt_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"
            }
        """.trimIndent()

        val response = NetworkClient.gson.fromJson(json, PairResponse::class.java)
        assertEquals("success", response.status)
        assertEquals("11111111-2222-3333-4444-555555555555", response.deviceId)
        assertTrue(response.deviceToken.startsWith("sdt_"))
    }

    @Test
    fun testTelemetryRequestSerialization() {
        val reading = ReadingDto(
            readingId = "test-reading-123",
            recordedAt = "2026-09-27T15:40:00Z",
            heartRate = 150,
            contactStatus = true,
            spo2 = 98,
            movement = 0.02,
            fallDetected = false
        )
        val request = TelemetryRequest(readings = listOf(reading))
        val json = NetworkClient.gson.toJson(request)

        assertTrue(json.contains("reading_id"))
        assertTrue(json.contains("recorded_at"))
        assertTrue(json.contains("heart_rate"))
        assertTrue(json.contains("contact_status"))
    }

    @Test
    fun testTelemetryResponseParsingWithInvalidReadings() {
        val json = """
            {
                "status": "success",
                "accepted": 98,
                "duplicates": 1,
                "rejected_out_of_window": 0,
                "invalid": 1,
                "invalid_readings": [
                    {
                        "index": 2,
                        "reading_id": "invalid-id-99",
                        "issues": ["heart_rate out of range"]
                    }
                ],
                "event_logged": false
            }
        """.trimIndent()

        val response = NetworkClient.gson.fromJson(json, TelemetryResponse::class.java)
        assertEquals("success", response.status)
        assertEquals(98, response.accepted)
        assertEquals(1, response.duplicates)
        assertEquals(1, response.invalid)
        assertNotNull(response.invalidReadings)
        assertEquals("invalid-id-99", response.invalidReadings!![0].readingId)
    }

    @Test
    fun testEventRequestTypes() {
        val request = EventRequest(
            eventId = "event-uuid-123",
            type = "sos",
            recordedAt = "2026-09-27T15:41:00Z",
            heartRate = 160,
            contactStatus = true
        )
        val json = NetworkClient.gson.toJson(request)
        assertTrue(json.contains("\"type\":\"sos\""))
        assertTrue(json.contains("\"event_id\":\"event-uuid-123\""))
    }
}
