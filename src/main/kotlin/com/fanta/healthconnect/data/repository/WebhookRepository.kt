package com.fanta.healthconnect.data.repository

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.fanta.healthconnect.data.model.HealthDataPayload
import com.fanta.healthconnect.data.model.HealthRecord
import com.fanta.healthconnect.data.network.WebhookService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebhookRepository @Inject constructor(
    private val webhookService: WebhookService,
    @ApplicationContext private val context: Context
) {

    suspend fun sendHealthData(
        webhookUrl: String,
        healthRecords: List<HealthRecord>,
        userId: String = "default_user"
    ): Result<Unit> {
        return try {
            Log.d("WebhookRepository", "웹훅 전송 시작: URL=$webhookUrl, 데이터 개수=${healthRecords.size}")
            
            val deviceId = getDeviceId()
            val payload = HealthDataPayload(
                timestamp = Instant.now().toString(),
                deviceId = deviceId,
                userId = userId,
                dataType = "health_sync",
                data = healthRecords
            )

            Log.d("WebhookRepository", "페이로드 생성 완료: deviceId=$deviceId, userId=$userId")

            val response = webhookService.sendHealthData(webhookUrl, payload)
            
            Log.d("WebhookRepository", "HTTP 응답: 코드=${response.code()}, 메시지=${response.message()}")
            
            if (response.isSuccessful) {
                Log.i("WebhookRepository", "웹훅 전송 성공!")
                Result.success(Unit)
            } else {
                val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                Log.e("WebhookRepository", "웹훅 전송 실패: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e("WebhookRepository", "웹훅 전송 중 예외 발생", e)
            Result.failure(e)
        }
    }

    private fun getDeviceId(): String {
        return try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown_device"
        } catch (e: Exception) {
            "unknown_device"
        }
    }
} 