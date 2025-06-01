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

            // Health Connect 초기화 재시도 로직 (백그라운드 실행 시 중요)
            var healthConnectReady = false
            var attempts = 0
            val maxAttempts = 3
            
            while (!healthConnectReady && attempts < maxAttempts) {
                attempts++
                Log.d(TAG, "Health Connect initialization attempt $attempts/$maxAttempts")
                
                // Health Connect 사용 가능 여부 확인
                if (!healthConnectRepository.isHealthConnectAvailable()) {
                    Log.w(TAG, "Health Connect is not available (attempt $attempts)")
                    if (attempts < maxAttempts) {
                        kotlinx.coroutines.delay(2000) // 2초 대기 후 재시도
                        continue
                    } else {
                        Log.e(TAG, "Health Connect is not available after $maxAttempts attempts")
                        return Result.failure()
                    }
                }

                // 권한 확인
                if (!healthConnectRepository.checkPermissions()) {
                    Log.w(TAG, "Health Connect permissions not granted (attempt $attempts)")
                    if (attempts < maxAttempts) {
                        kotlinx.coroutines.delay(2000) // 2초 대기 후 재시도
                        continue
                    } else {
                        Log.e(TAG, "Health Connect permissions not granted after $maxAttempts attempts")
                        return Result.failure()
                    }
                }
                
                healthConnectReady = true
                Log.d(TAG, "Health Connect ready after $attempts attempts")
            }

            Log.d(TAG, "Fetching today's health data for background sync...")

            // 오늘의 건강 데이터 수집 (재시도 로직 포함)
            var totalRecords = 0
            var dataFetchAttempts = 0
            val maxDataAttempts = 2
            
            while (totalRecords == 0 && dataFetchAttempts < maxDataAttempts) {
                dataFetchAttempts++
                Log.d(TAG, "Health data fetch attempt $dataFetchAttempts/$maxDataAttempts")
                
                healthConnectRepository.getTodayHealthData().collect { healthRecords ->
                    totalRecords = healthRecords.size
                    Log.d(TAG, "Retrieved ${healthRecords.size} health records for today (attempt $dataFetchAttempts)")
                    
                    if (healthRecords.isNotEmpty()) {
                        // 데이터 타입별 개수 로깅
                        val dataTypeCounts = healthRecords.groupBy { it.type }.mapValues { it.value.size }
                        Log.d(TAG, "Today's data by type: $dataTypeCounts")
                        
                        // 웹훅으로 데이터 전송
                        Log.d(TAG, "Sending today's data to webhook: $webhookUrl")
                        val result = webhookRepository.sendHealthData(
                            webhookUrl = webhookUrl,
                            healthRecords = healthRecords,
                            userId = userId
                        )

                        if (result.isSuccess) {
                            // 성공 시 마지막 동기화 시간 업데이트
                            userPreferences.setLastSyncTime(Instant.now())
                            Log.d(TAG, "Today's health data sync completed successfully, updated lastSyncTime to ${Instant.now()}")
                        } else {
                            val error = result.exceptionOrNull()
                            Log.e(TAG, "Failed to send today's data to webhook", error)
                            return@collect
                        }
                    } else {
                        Log.d(TAG, "No health data found for today (attempt $dataFetchAttempts)")
                        if (dataFetchAttempts < maxDataAttempts) {
                            Log.d(TAG, "Waiting 3 seconds before retry...")
                        }
                    }
                }
                
                // 데이터가 없고 재시도가 남았다면 대기
                if (totalRecords == 0 && dataFetchAttempts < maxDataAttempts) {
                    kotlinx.coroutines.delay(3000) // 3초 대기 후 재시도
                }
            }

            Log.d(TAG, "Background sync completed successfully with $totalRecords records")
            
            // 성공 시간을 OutputData에 저장
            val finishTime = Instant.now()
            val outputData = Data.Builder()
                .putString("finishTime", finishTime.toString())
                .putString("status", "success")
                .putInt("recordCount", totalRecords)
                .putInt("initAttempts", attempts)
                .putInt("dataAttempts", dataFetchAttempts)
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