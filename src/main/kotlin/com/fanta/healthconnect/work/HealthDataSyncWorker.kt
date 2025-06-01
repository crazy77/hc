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

            // Health Connect 사용 가능 여부 확인
            if (!healthConnectRepository.isHealthConnectAvailable()) {
                Log.e(TAG, "Health Connect is not available")
                return Result.failure()
            }

            // 권한 확인
            if (!healthConnectRepository.checkPermissions()) {
                Log.e(TAG, "Health Connect permissions not granted")
                return Result.failure()
            }

            // 마지막 동기화 시간 확인
            val lastSyncTime = userPreferences.lastSyncTime.first()
                ?: Instant.now().minus(1, ChronoUnit.DAYS)

            Log.d(TAG, "Last sync time: $lastSyncTime")

            // 건강 데이터 수집
            var totalRecords = 0
            healthConnectRepository.getHealthDataSince(lastSyncTime).collect { healthRecords ->
                totalRecords = healthRecords.size
                Log.d(TAG, "Retrieved ${healthRecords.size} health records")
                
                if (healthRecords.isNotEmpty()) {
                    // 웹훅으로 데이터 전송
                    Log.d(TAG, "Sending data to webhook: $webhookUrl")
                    val result = webhookRepository.sendHealthData(
                        webhookUrl = webhookUrl,
                        healthRecords = healthRecords,
                        userId = userId
                    )

                    if (result.isSuccess) {
                        // 성공 시 마지막 동기화 시간 업데이트
                        userPreferences.setLastSyncTime(Instant.now())
                        Log.d(TAG, "Health data sync completed successfully")
                    } else {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Failed to send data to webhook", error)
                        return@collect
                    }
                } else {
                    Log.d(TAG, "No new health data to sync")
                }
            }

            Log.d(TAG, "Work completed successfully with $totalRecords records")
            
            // 성공 시간을 OutputData에 저장
            val finishTime = Instant.now()
            val outputData = Data.Builder()
                .putString("finishTime", finishTime.toString())
                .putString("status", "success")
                .putInt("recordCount", totalRecords)
                .build()
            
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during health data sync", e)
            
            // 실패 시간을 OutputData에 저장
            val finishTime = Instant.now()
            val failureData = Data.Builder()
                .putString("finishTime", finishTime.toString())
                .putString("status", "failure")
                .putString("error", e.message ?: "Unknown error")
                .putString("errorType", e.javaClass.simpleName)
                .build()
            
            Log.d(TAG, "Failure data created: finishTime=$finishTime, error=${e.message}")
            
            // 실패 횟수에 따라 재시도 여부 결정
            val attemptCount = runAttemptCount
            Log.d(TAG, "Attempt count: $attemptCount")
            
            return if (attemptCount < 3) {
                Log.d(TAG, "Retrying work (attempt $attemptCount), will retry after backoff")
                // 재시도할 때도 실패 데이터를 포함하여 UI에서 확인 가능하도록
                Result.retry()
            } else {
                Log.e(TAG, "Max retry attempts reached, failing work with data")
                Result.failure(failureData)
            }
        }
    }
} 