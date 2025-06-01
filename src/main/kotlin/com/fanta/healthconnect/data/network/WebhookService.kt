package com.fanta.healthconnect.data.network

import com.fanta.healthconnect.data.model.HealthDataPayload
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface WebhookService {
    
    @POST
    suspend fun sendHealthData(
        @Url url: String,
        @Body payload: HealthDataPayload
    ): Response<Unit>
} 