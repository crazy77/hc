package com.fanta.healthconnect.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.temporal.ChronoUnit
import com.fanta.healthconnect.data.preferences.UserPreferences
import com.fanta.healthconnect.data.repository.HealthConnectRepository
import com.fanta.healthconnect.data.repository.WebhookRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HealthDataSyncWorkerEntryPoint {
    fun healthConnectRepository(): HealthConnectRepository
    fun webhookRepository(): WebhookRepository
    fun userPreferences(): UserPreferences
}

class HealthDataSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val entryPoint = EntryPointAccessors.fromApplication(
        applicationContext,
        HealthDataSyncWorkerEntryPoint::class.java
    )
    
    private val healthConnectRepository = entryPoint.healthConnectRepository()
    private val webhookRepository = entryPoint.webhookRepository()
    private val userPreferences = entryPoint.userPreferences()

    companion object {
        private const val TAG = "HealthDataSyncWorker"
    }

    override suspend fun doWork(): Result {
        val startTime = Instant.now()
        Log.d(TAG, "Starting health data sync work at $startTime")
        
        return try {
            // 설정 확인
            val webhookUrl = userPreferences.webhookUrl.first()
            val userId = userPreferences.userId.first()
            val isSyncEnabled = userPreferences.isSyncEnabled.first()

            Log.d(TAG, "Sync enabled: $isSyncEnabled, Webhook URL configured: ${webhookUrl.isNotBlank()}")

            if (!isSyncEnabled) {
                Log.d(TAG, "Sync is disabled, skipping")
                return Result.success()
            }
            
            if (webhookUrl.isBlank()) {
                Log.w(TAG, "Webhook URL is not configured")
                return Result.success()
            }

            // 백그라운드 실행 제약으로 인해 포그라운드 서비스로 실제 동기화 수행
            Log.d(TAG, "Starting foreground service for health data sync due to background restrictions")
            HealthDataSyncForegroundService.startSync(applicationContext)
            
            // WorkManager는 포그라운드 서비스 시작 후 즉시 성공 반환
            val finishTime = Instant.now()
            val outputData = Data.Builder()
                .putString("finishTime", finishTime.toString())
                .putString("status", "foreground_service_started")
                .putString("message", "Foreground service started for health data sync")
                .build()
            
            Log.d(TAG, "Foreground service started successfully")
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service for health data sync", e)
            
            // 실패 시간을 OutputData에 저장
            val finishTime = Instant.now()
            val failureData = Data.Builder()
                .putString("finishTime", finishTime.toString())
                .putString("status", "failure")
                .putString("error", e.message ?: "Unknown error")
                .putString("errorType", e.javaClass.simpleName)
                .build()
            
            Log.d(TAG, "Failure data created: finishTime=$finishTime, error=${e.message}")
            Result.failure(failureData)
        }
    }
} 