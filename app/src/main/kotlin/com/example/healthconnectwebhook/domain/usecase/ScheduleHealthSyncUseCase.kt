package com.example.healthconnectwebhook.domain.usecase

import android.content.Context
import androidx.work.*
import com.example.healthconnectwebhook.data.preferences.UserPreferences
import com.example.healthconnectwebhook.work.HealthDataSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleHealthSyncUseCase @Inject constructor(
    private val workManager: WorkManager,
    private val userPreferences: UserPreferences
) {

    companion object {
        private const val WORK_NAME = "health_data_sync"
    }

    suspend fun startPeriodicSync() {
        val intervalMinutes = userPreferences.syncInterval.first()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            15, TimeUnit.MINUTES // 최소 15분 flex 기간
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
    }

    suspend fun stopPeriodicSync() {
        workManager.cancelUniqueWork(WORK_NAME)
        userPreferences.setSyncEnabled(false)
    }

    suspend fun triggerManualSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val manualSyncRequest = OneTimeWorkRequestBuilder<HealthDataSyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(manualSyncRequest)
    }

    suspend fun updateSyncInterval(intervalMinutes: Int) {
        userPreferences.setSyncInterval(intervalMinutes)
        
        val isSyncEnabled = userPreferences.isSyncEnabled.first()
        if (isSyncEnabled) {
            // 동기화가 활성화되어 있으면 새로운 간격으로 재시작
            startPeriodicSync()
        }
    }

    fun getSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME)
} 