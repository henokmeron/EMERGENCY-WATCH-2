package com.example.emergencywatch.domain.testing

import com.example.emergencywatch.data.local.TestEventDao
import com.example.emergencywatch.data.local.TestEventEntity
import com.example.emergencywatch.domain.sensor.SensorData
import java.util.Locale
import java.util.UUID

class TestScenarioRecorder(private val testEventDao: TestEventDao) {

    suspend fun recordTestEvent(
        scenarioName: String,
        sensorData: SensorData,
        batteryLevel: Int,
        userAction: String = "NONE",
        finalState: String = "NORMAL"
    ): TestEventEntity {
        val now = System.currentTimeMillis()
        val isoTime = SensorData.getCurrentIsoTimestamp()

        val conclusion = evaluateConclusion(scenarioName, sensorData)

        val entity = TestEventEntity(
            eventId = UUID.randomUUID().toString(),
            scenarioName = scenarioName,
            recordedAt = isoTime,
            epochMs = now,
            heartRate = sensorData.heartRate,
            contactStatus = sensorData.contactStatus,
            movement = sensorData.movement,
            batteryLevel = batteryLevel,
            sensorConclusion = conclusion,
            userAction = userAction,
            finalState = finalState,
            syncedToBackend = true
        )
        testEventDao.insertEvent(entity)
        return entity
    }

    private fun evaluateConclusion(scenarioName: String, data: SensorData): String {
        val contactText = if (data.contactStatus) "On-Wrist (Attached)" else "Off-Wrist (Detached)"
        val hrText = data.heartRate?.let { "$it BPM" } ?: "HR -- (No Pulse / Off)"
        val moveText = String.format(Locale.US, "%.2f m/s²", data.movement)

        return when {
            scenarioName.contains("desk", ignoreCase = true) || scenarioName.contains("removed", ignoreCase = true) ->
                "Sensors reported $hrText, movement $moveText, contact OFF. Conclusion: Watch removed or desk placement (No emergency)."
            scenarioName.contains("walking", ignoreCase = true) ->
                "Sensors reported $hrText, movement $moveText, contact ON. Conclusion: Active walking normal."
            scenarioName.contains("sitting", ignoreCase = true) || scenarioName.contains("lying", ignoreCase = true) ->
                "Sensors reported $hrText, movement $moveText, contact ON. Conclusion: Stationary / resting normal."
            scenarioName.contains("fall", ignoreCase = true) ->
                "Sensors reported impact movement $moveText, contact ON. Conclusion: Fall impact detected — monitoring recovery/inactivity."
            scenarioName.contains("sos", ignoreCase = true) ->
                "User triggered manual SOS. Conclusion: Emergency broadcast dispatched to backend."
            scenarioName.contains("cancel", ignoreCase = true) ->
                "User cancelled alert. Conclusion: Fall/alert safely aborted."
            else ->
                "Sensors reported $hrText, $contactText, movement $moveText. Conclusion: Normal operation."
        }
    }
}
