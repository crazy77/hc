package com.fanta.healthconnect.domain.usecase

import android.content.Context
import androidx.work.*
import com.fanta.healthconnect.data.preferences.UserPreferences
import com.fanta.healthconnect.work.HealthDataSyncWorker
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
        private const val IMMEDIATE_WORK_NAME = "health_data_sync_immediate"
    }

    suspend fun startPeriodicSync() {
        val intervalMinutes = userPreferences.syncInterval.first()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<HealthDataSyncWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // flex 기간
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .setInitialDelay(0, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
    }

    suspend fun stopPeriodicSync() {
        workManager.cancelUniqueWork(WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        userPreferences.setSyncEnabled(false)
    }

    suspend fun triggerManualSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val manualSyncRequest = OneTimeWorkRequestBuilder<HealthDataSyncWorker>()
            .setConstraints(constraints)
            .addTag("manual_sync")
            .build()

        workManager.enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            manualSyncRequest
        )
    }

    suspend fun updateSyncInterval(intervalMinutes: Int) {
        userPreferences.setSyncInterval(intervalMinutes)
        
        val isSyncEnabled = userPreferences.isSyncEnabled.first()
        if (isSyncEnabled) {
            startPeriodicSync()
        }
    }

    suspend fun getAllWorkInfo(): List<WorkInfo> {
        return emptyList() // 간단하게 빈 리스트 반환 (진단 기능은 나중에 개선)
    }

    fun getSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME)
    
    fun getAllSyncWorkInfo() = workManager.getWorkInfosForUniqueWorkLiveData(WORK_NAME)
    
    suspend fun getDiagnosticInfo(): Map<String, Any> {
        return mapOf(
            "total_work_count" to 0,
            "periodic_work_count" to 0,
            "work_states" to emptyMap<String, Int>(),
            "last_enqueue_time" to "none",
            "constraints_met" to false
        )
    }
} 