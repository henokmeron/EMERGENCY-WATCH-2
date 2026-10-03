package com.example.emergencywatch

import com.example.emergencywatch.data.config.WatchConfigStore
import com.example.emergencywatch.data.config.WatchOperationalConfig
import com.example.emergencywatch.data.local.TelemetryDao
import com.example.emergencywatch.data.local.TelemetryEntity
import com.example.emergencywatch.data.remote.WatchApiService
import com.example.emergencywatch.data.remote.model.ConfigResponse
import com.example.emergencywatch.data.remote.model.EventRequest
import com.example.emergencywatch.data.remote.model.EventResponse
import com.example.emergencywatch.data.remote.model.HeartbeatRequest
import com.example.emergencywatch.data.remote.model.HeartbeatResponse
import com.example.emergencywatch.data.remote.model.PairRequest
import com.example.emergencywatch.data.remote.model.PairResponse
import com.example.emergencywatch.data.remote.model.TelemetryRequest
import com.example.emergencywatch.data.remote.model.TelemetryResponse
import com.example.emergencywatch.data.remote.model.TokenRefreshResponse
import com.example.emergencywatch.data.remote.model.WatchConfig
import com.example.emergencywatch.data.repository.TelemetryRepository
import com.example.emergencywatch.data.repository.TelemetrySyncResult
import com.example.emergencywatch.domain.sensor.MovementProcessor
import com.example.emergencywatch.domain.sensor.SensorData
import com.example.emergencywatch.domain.telemetry.TelemetrySampler
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TelemetryRateControlTest {

    @Test
    fun fiftySensorCallbacks_createAtMostOneRoomRecord_atFiveSecondSample() = runBlocking {
        val clock = AtomicLong(0L)
        val dao = FakeTelemetryDao()
        val config = MutableOperationalConfig(sampleSeconds = 5, uploadSeconds = 60, batchSize = 100)
        val repo = TelemetryRepository(
            apiService = FakeWatchApi(),
            telemetryDao = dao,
            configStore = config,
            telemetrySampler = TelemetrySampler { clock.get() }
        )

        repeat(50) {
            repo.recordReading(SensorData(heartRate = 80, movement = 1.0))
            clock.addAndGet(20L) // simulate 50 Hz accel
        }

        assertEquals(1, dao.insertCount.get())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun callbacksWithinSamplingInterval_areNotPersisted() = runBlocking {
        val clock = AtomicLong(0L)
        val dao = FakeTelemetryDao()
        val repo = repo(dao, clock, sampleSeconds = 5)

        assertTrue(repo.recordReading(SensorData(movement = 1.0)))
        clock.set(2_500L)
        assertFalse(repo.recordReading(SensorData(movement = 2.0)))
        assertEquals(1, dao.insertCount.get())
    }

    @Test
    fun callbackAfterSamplingInterval_persistsNextRecord() = runBlocking {
        val clock = AtomicLong(0L)
        val dao = FakeTelemetryDao()
        val repo = repo(dao, clock, sampleSeconds = 5)

        assertTrue(repo.recordReading(SensorData(movement = 1.0)))
        clock.set(5_000L)
        assertTrue(repo.recordReading(SensorData(movement = 2.0)))
        assertEquals(2, dao.insertCount.get())
    }

    @Test
    fun changingConfiguredSampleInterval_changesPersistenceRate() = runBlocking {
        val clock = AtomicLong(0L)
        val dao = FakeTelemetryDao()
        val config = MutableOperationalConfig(sampleSeconds = 2, uploadSeconds = 60, batchSize = 100)
        val sampler = TelemetrySampler { clock.get() }
        val repo = TelemetryRepository(
            FakeWatchApi(),
            dao,
            config,
            sampler
        )

        assertTrue(repo.recordReading(SensorData()))
        clock.set(1_999L)
        assertFalse(repo.recordReading(SensorData()))
        clock.set(2_000L)
        assertTrue(repo.recordReading(SensorData()))
        assertEquals(2, dao.insertCount.get())

        config.sampleSeconds = 10
        sampler.reset()
        dao.rows.clear()
        dao.insertCount.set(0)
        clock.set(0L)

        var accepted = 0
        repeat(8) {
            if (repo.recordReading(SensorData())) accepted++
            clock.addAndGet(1_000L)
        }
        assertEquals(1, accepted)
        assertEquals(1, dao.insertCount.get())
    }

    @Test
    fun configuredUploadInterval_defaultsAndUpdatesFromWatchConfig() {
        val config = MutableOperationalConfig(
            sampleSeconds = WatchConfigStore.DEFAULT_SAMPLE_INTERVAL_SECONDS,
            uploadSeconds = WatchConfigStore.DEFAULT_UPLOAD_INTERVAL_SECONDS,
            batchSize = WatchConfigStore.DEFAULT_MAX_BATCH_SIZE
        )
        assertEquals(60, config.uploadIntervalSeconds())
        assertEquals(60_000L, config.uploadIntervalMs())

        config.updateFrom(
            WatchConfig(
                sampleIntervalSeconds = 5,
                uploadIntervalSeconds = 90,
                maxBatchSize = 100
            )
        )
        assertEquals(90, config.uploadIntervalSeconds())
        assertEquals(90_000L, config.uploadIntervalMs())
    }

    @Test
    fun configuredBatchSize_isRespectedOnSync() = runBlocking {
        val dao = FakeTelemetryDao()
        repeat(25) {
            dao.insertReading(
                TelemetryEntity(
                    readingId = "id-$it",
                    recordedAt = "2026-09-27T22:00:00Z",
                    movement = 0.0
                )
            )
        }
        val api = FakeWatchApi()
        val config = MutableOperationalConfig(sampleSeconds = 5, uploadSeconds = 60, batchSize = 10)
        val repo = TelemetryRepository(api, dao, config)

        val result = repo.syncTelemetryBatch()
        assertTrue(result is TelemetrySyncResult.Success)
        assertEquals(10, api.lastTelemetryBatchSize)
        assertEquals(15, dao.rows.size) // 25 - 10 deleted after success
    }

    @Test
    fun rawFallDetection_isNotThrottledByTelemetrySampler() {
        val processor = MovementProcessor()
        var fallHits = 0
        repeat(50) { i ->
            val x = if (i == 25) 20f else 0f
            val y = if (i == 25) 20f else 0f
            val z = if (i == 25) 15f else 9.81f
            processor.processAccelerometer(x, y, z)
            val (fall, _) = processor.checkFallImpact(x, y, z)
            if (fall) fallHits++
        }
        assertEquals(1, fallHits)
    }

    @Test
    fun sosEvent_bypassesTelemetrySamplingGate() = runBlocking {
        val clock = AtomicLong(0L)
        val dao = FakeTelemetryDao()
        val api = FakeWatchApi()
        val repo = TelemetryRepository(
            api,
            dao,
            MutableOperationalConfig(5, 60, 100),
            TelemetrySampler { clock.get() }
        )

        assertTrue(repo.recordReading(SensorData(heartRate = 70)))
        clock.set(100L)
        assertFalse(repo.recordReading(SensorData(heartRate = 71)))

        val result = repo.sendEvent("sos", SensorData(heartRate = 72, contactStatus = true))
        assertTrue(result.isSuccess)
        assertEquals(1, api.eventCallCount.get())
        assertEquals("sos", api.lastEventType)
        assertEquals(1, dao.insertCount.get())
    }

    private fun repo(
        dao: FakeTelemetryDao,
        clock: AtomicLong,
        sampleSeconds: Int
    ) = TelemetryRepository(
        FakeWatchApi(),
        dao,
        MutableOperationalConfig(sampleSeconds, 60, 100),
        TelemetrySampler { clock.get() }
    )
}

private class MutableOperationalConfig(
    var sampleSeconds: Int,
    var uploadSeconds: Int,
    var batchSize: Int
) : WatchOperationalConfig {
    override fun sampleIntervalSeconds(): Int = sampleSeconds
    override fun uploadIntervalSeconds(): Int = uploadSeconds
    override fun maxBatchSize(): Int = batchSize
    override fun updateFrom(config: WatchConfig) {
        sampleSeconds = (config.sampleIntervalSeconds ?: sampleSeconds).coerceAtLeast(1)
        uploadSeconds = (config.uploadIntervalSeconds ?: uploadSeconds).coerceAtLeast(1)
        batchSize = (config.maxBatchSize ?: batchSize).coerceAtLeast(1)
    }
}

private class FakeTelemetryDao : TelemetryDao {
    val rows = mutableListOf<TelemetryEntity>()
    val insertCount = AtomicInteger(0)

    override suspend fun insertReading(reading: TelemetryEntity) {
        insertCount.incrementAndGet()
        rows.add(reading)
    }

    override suspend fun insertReadings(readings: List<TelemetryEntity>) {
        readings.forEach { insertReading(it) }
    }

    override suspend fun getUnsyncedReadings(limit: Int): List<TelemetryEntity> =
        rows.sortedBy { it.createdAtEpoch }.take(limit)

    override suspend fun deleteReadings(readingIds: List<String>) {
        rows.removeAll { it.readingId in readingIds }
    }

    override suspend fun deleteOldReadings(cutoffEpochMillis: Long) {
        rows.removeAll { it.createdAtEpoch < cutoffEpochMillis }
    }

    override suspend fun getUnsyncedCount(): Int = rows.size

    override suspend fun clearAll() {
        rows.clear()
    }
}

private class FakeWatchApi : WatchApiService {
    var lastTelemetryBatchSize: Int = 0
    val eventCallCount = AtomicInteger(0)
    var lastEventType: String? = null

    override suspend fun pairWatch(request: PairRequest): Response<PairResponse> =
        Response.error(500, "".toResponseBody("text/plain".toMediaType()))

    override suspend fun sendTelemetry(request: TelemetryRequest): Response<TelemetryResponse> {
        lastTelemetryBatchSize = request.readings.size
        return Response.success(
            TelemetryResponse(
                status = "success",
                accepted = request.readings.size,
                duplicates = 0,
                rejectedOutOfWindow = 0,
                invalid = 0
            )
        )
    }

    override suspend fun sendEvent(request: EventRequest): Response<EventResponse> {
        eventCallCount.incrementAndGet()
        lastEventType = request.type
        return Response.success(EventResponse(status = "success", eventLogged = true))
    }

    override suspend fun sendHeartbeat(request: HeartbeatRequest): Response<HeartbeatResponse> =
        Response.success(HeartbeatResponse(status = "success", config = null))

    override suspend fun getConfig(): Response<ConfigResponse> =
        Response.success(ConfigResponse(status = "success", config = null))

    override suspend fun refreshToken(): Response<TokenRefreshResponse> =
        Response.error(500, "".toResponseBody("text/plain".toMediaType()))
}
