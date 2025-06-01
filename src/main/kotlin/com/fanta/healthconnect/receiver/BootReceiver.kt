package com.fanta.healthconnect.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot completed or package updated: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // WorkManager를 통해 동기화 재시작
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val workManager = WorkManager.getInstance(context)
                        restartHealthSyncIfEnabled(context, workManager)
                    } catch (e: Exception) {
                        Log.e("BootReceiver", "Error restarting sync after boot", e)
                    }
                }
            }
        }
    }
    
    private fun restartHealthSyncIfEnabled(context: Context, workManager: WorkManager) {
        try {
            Log.d("BootReceiver", "Checking and restarting health data sync")
            
            // 기존 작업이 있는지 확인하고 새로 시작
            // SharedPreferences에서 동기화 설정 확인
            val sharedPrefs = context.getSharedPreferences(
                "user_preferences", Context.MODE_PRIVATE
            )
            
            val isSyncEnabled = sharedPrefs.getBoolean("sync_enabled", false)
            val syncInterval = sharedPrefs.getInt("sync_interval", 15)
            
            if (isSyncEnabled) {
                Log.d("BootReceiver", "Restarting health data sync with interval: $syncInterval minutes")
                
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(false)
                    .build()

                val effectiveInterval = maxOf(syncInterval.toLong(), 15L)
                
                val syncRequest = PeriodicWorkRequestBuilder<com.fanta.healthconnect.work.HealthDataSyncWorker>(
                    effectiveInterval, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        30, TimeUnit.SECONDS
                    )
                    .addTag("health_sync")
                    .addTag("periodic_sync")
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    "health_data_sync",
                    ExistingPeriodicWorkPolicy.KEEP,
                    syncRequest
                )
                
                Log.d("BootReceiver", "Health data sync restarted successfully")
            } else {
                Log.d("BootReceiver", "Health data sync is disabled, not restarting")
            }
            
        } catch (e: Exception) {
            Log.e("BootReceiver", "Error in restartHealthSyncIfEnabled", e)
        }
    }
} 