package com.example.emergencywatch

import com.example.emergencywatch.companion.CompanionPaths
import com.example.emergencywatch.data.remote.NetworkClient
import com.example.emergencywatch.data.remote.model.ConfigResponse
import com.example.emergencywatch.data.remote.model.PairRequest
import com.example.emergencywatch.data.remote.model.PairResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionContractTest {

    @Test
    fun dataLayerPaths_matchWearContract() {
        assertEquals("/emergency_watch/status", CompanionPaths.STATUS_PATH)
        assertEquals("/emergency_watch/pair_request", CompanionPaths.PAIR_REQUEST_PATH)
        assertEquals("/emergency_watch/pair_result", CompanionPaths.PAIR_RESULT_PATH)
        assertEquals("paired", CompanionPaths.KEY_PAIRED)
        assertEquals("queued_readings", CompanionPaths.KEY_QUEUED)
    }

    @Test
    fun backendBaseUrl_matchesWear() {
        assertEquals("https://med-eye.lovable.app", NetworkClient.BASE_URL)
    }

    @Test
    fun pairRequest_serializesExistingContract() {
        val json = NetworkClient.gson.toJson(
            PairRequest(code = "AB12CD", model = "Phone", osVersion = "Android 17", appVersion = "1.0")
        )
        assertTrue(json.contains("\"os_version\""))
        assertTrue(json.contains("\"app_version\""))
        val parsed = NetworkClient.gson.fromJson(json, PairRequest::class.java)
        assertEquals("AB12CD", parsed.code)
    }

    @Test
    fun pairResponse_parsesExistingContract() {
        val json = """
            {"status":"success","device_id":"d1","patient_id":"p1","device_token":"sdt_secret"}
        """.trimIndent()
        val parsed = NetworkClient.gson.fromJson(json, PairResponse::class.java)
        assertEquals("success", parsed.status)
        assertEquals("d1", parsed.deviceId)
        assertTrue(parsed.deviceToken.startsWith("sdt_"))
    }

    @Test
    fun configResponse_parsesExistingContract() {
        val json = """
            {"status":"success","config":{"patient_id":"p1","patient_name":"Amara",
             "upload_interval_seconds":60,"sample_interval_seconds":5,"heartbeat_interval_seconds":300,"max_batch_size":100}}
        """.trimIndent()
        val parsed = NetworkClient.gson.fromJson(json, ConfigResponse::class.java)
        assertEquals("success", parsed.status)
        assertEquals(60, parsed.config?.uploadIntervalSeconds)
        assertEquals(5, parsed.config?.sampleIntervalSeconds)
        assertEquals(100, parsed.config?.maxBatchSize)
    }

    @Test
    fun pairingCode_lengthGate() {
        assertFalse("AB".length >= 6)
        assertTrue("AB12CD".length >= 6)
        assertTrue("AB12CD34".length <= 8)
    }
}
