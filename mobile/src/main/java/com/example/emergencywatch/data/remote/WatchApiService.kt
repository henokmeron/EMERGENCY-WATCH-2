package com.example.emergencywatch.data.remote

import com.example.emergencywatch.data.remote.model.ConfigResponse
import com.example.emergencywatch.data.remote.model.PairRequest
import com.example.emergencywatch.data.remote.model.PairResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Subset of the watch API used by the phone companion.
 * Pairing is normally forwarded to the watch via Data Layer so the device
 * token stays on the watch; these endpoints exist for reachability/config
 * checks when a bearer token is available in the future.
 */
interface WatchApiService {

    @POST("/api/public/watch/pair")
    suspend fun pairWatch(@Body request: PairRequest): Response<PairResponse>

    @GET("/api/public/watch/config")
    suspend fun getConfig(
        @Header("Authorization") authorization: String
    ): Response<ConfigResponse>
}
