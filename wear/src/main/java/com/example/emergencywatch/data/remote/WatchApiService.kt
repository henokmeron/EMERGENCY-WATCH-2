package com.example.emergencywatch.data.remote

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WatchApiService {

    @POST("/api/public/watch/pair")
    suspend fun pairWatch(@Body request: PairRequest): Response<PairResponse>

    @POST("/api/public/watch/telemetry")
    suspend fun sendTelemetry(@Body request: TelemetryRequest): Response<TelemetryResponse>

    @POST("/api/public/watch/event")
    suspend fun sendEvent(@Body request: EventRequest): Response<EventResponse>

    @POST("/api/public/watch/heartbeat")
    suspend fun sendHeartbeat(@Body request: HeartbeatRequest): Response<HeartbeatResponse>

    @GET("/api/public/watch/config")
    suspend fun getConfig(): Response<ConfigResponse>

    @POST("/api/public/watch/token/refresh")
    suspend fun refreshToken(): Response<TokenRefreshResponse>
}
