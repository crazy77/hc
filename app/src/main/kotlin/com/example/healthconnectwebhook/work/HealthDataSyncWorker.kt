package com.example.healthconnectwebhook.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthconnectwebhook.data.preferences.UserPreferences
import com.example.healthconnectwebhook.data.repository.HealthConnectRepository
import com.example.healthconnectwebhook.data.repository.WebhookRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit

@HiltWorker
class HealthDataSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val healthConnectRepository: HealthConnectRepository,
    private val webhookRepository: WebhookRepository,
    private val userPreferences: UserPreferences
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // 설정 확인
            val webhookUrl = userPreferences.webhookUrl.first()
            val userId = userPreferences.userId.first()
            val isSyncEnabled = userPreferences.isSyncEnabled.first()

            if (!isSyncEnabled || webhookUrl.isBlank()) {
                return Result.success()
            }

            // 권한 확인
            if (!healthConnectRepository.checkPermissions()) {
                return Result.failure()
            }

            // 마지막 동기화 시간 확인
            val lastSyncTime = userPreferences.lastSyncTime.first()
                ?: Instant.now().minus(1, ChronoUnit.DAYS)

            // 건강 데이터 수집
            healthConnectRepository.getHealthDataSince(lastSyncTime).collect { healthRecords ->
                if (healthRecords.isNotEmpty()) {
                    // 웹훅으로 데이터 전송
                    val result = webhookRepository.sendHealthData(
                        webhookUrl = webhookUrl,
                        healthRecords = healthRecords,
                        userId = userId
                    )

                    if (result.isSuccess) {
                        // 성공 시 마지막 동기화 시간 업데이트
                        userPreferences.setLastSyncTime(Instant.now())
                    } else {
                        return@collect
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
} 