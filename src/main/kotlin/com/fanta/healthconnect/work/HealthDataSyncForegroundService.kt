package com.fanta.healthconnect.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import com.fanta.healthconnect.data.preferences.UserPreferences
import com.fanta.healthconnect.data.repository.HealthConnectRepository
import com.fanta.healthconnect.data.repository.WebhookRepository

@AndroidEntryPoint
class HealthDataSyncForegroundService : Service() {

    @Inject lateinit var healthConnectRepository: HealthConnectRepository
    @Inject lateinit var webhookRepository: WebhookRepository
    @Inject lateinit var userPreferences: UserPreferences

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "HealthSyncForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "health_sync_channel"
        
        fun startSync(context: Context) {
            val intent = Intent(context, HealthDataSyncForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "HealthDataSyncForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HealthDataSyncForegroundService started")
        
        val notification = createNotification("건강 데이터 동기화 중...")
        startForeground(NOTIFICATION_ID, notification)
        
        serviceScope.launch {
            try {
                performHealthDataSync()
            } catch (e: Exception) {
                Log.e(TAG, "Error in foreground sync", e)
            } finally {
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        Log.d(TAG, "HealthDataSyncForegroundService destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "건강 데이터 동기화",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "백그라운드 건강 데이터 동기화"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Health Connect 동기화")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private suspend fun performHealthDataSync() {
        Log.d(TAG, "Starting foreground health data sync")
        
        try {
            // 설정 확인
            val webhookUrl = userPreferences.webhookUrl.first()
            val userId = userPreferences.userId.first()
            val isSyncEnabled = userPreferences.isSyncEnabled.first()

            Log.d(TAG, "Foreground sync - enabled: $isSyncEnabled, webhook configured: ${webhookUrl.isNotBlank()}")

            if (!isSyncEnabled) {
                Log.d(TAG, "Sync is disabled, stopping foreground service")
                return
            }
            
            if (webhookUrl.isBlank()) {
                Log.w(TAG, "Webhook URL is not configured")
                return
            }

            // Health Connect 사용 가능성 및 권한 확인
            if (!healthConnectRepository.isHealthConnectAvailable()) {
                Log.e(TAG, "Health Connect is not available in foreground service")
                return
            }

            if (!healthConnectRepository.checkPermissions()) {
                Log.e(TAG, "Health Connect permissions not granted in foreground service")
                return
            }

            Log.d(TAG, "Foreground service: Fetching today's health data...")
            
            // 알림 업데이트
            val notification = createNotification("건강 데이터 수집 중...")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)

            // 오늘의 건강 데이터 수집 (삼성헬스 혈당 데이터 포함)
            var totalRecords = 0
            val today = java.time.LocalDate.now()
            healthConnectRepository.getHealthDataForDate(today).collect { healthRecords ->
                totalRecords = healthRecords.size
                Log.d(TAG, "Foreground service retrieved ${healthRecords.size} health records (including Samsung Health blood glucose)")
                
                if (healthRecords.isNotEmpty()) {
                    // 데이터 타입별 개수 로깅
                    val dataTypeCounts = healthRecords.groupBy { it.type }.mapValues { it.value.size }
                    Log.d(TAG, "Foreground sync data by type: $dataTypeCounts")
                    
                    // 알림 업데이트
                    val sendNotification = createNotification("웹훅으로 전송 중... (${healthRecords.size}개 항목)")
                    notificationManager.notify(NOTIFICATION_ID, sendNotification)
                    
                    // 웹훅으로 데이터 전송
                    Log.d(TAG, "Foreground service sending data to webhook: $webhookUrl")
                    val result = webhookRepository.sendHealthData(
                        webhookUrl = webhookUrl,
                        healthRecords = healthRecords,
                        userId = userId
                    )

                    if (result.isSuccess) {
                        // 성공 시 마지막 동기화 시간 업데이트
                        userPreferences.setLastSyncTime(Instant.now())
                        Log.d(TAG, "Foreground health data sync completed successfully")
                        
                        // 성공 알림
                        val successNotification = createNotification("동기화 완료 (${totalRecords}개 항목)")
                        notificationManager.notify(NOTIFICATION_ID, successNotification)
                        delay(2000) // 2초 후 서비스 종료
                    } else {
                        val error = result.exceptionOrNull()
                        Log.e(TAG, "Foreground service failed to send data to webhook", error)
                        
                        // 실패 알림
                        val failNotification = createNotification("동기화 실패: ${error?.message}")
                        notificationManager.notify(NOTIFICATION_ID, failNotification)
                        delay(3000) // 3초 후 서비스 종료
                    }
                } else {
                    Log.d(TAG, "Foreground service: No health data found for today")
                    
                    // 데이터 없음 알림
                    val noDataNotification = createNotification("오늘의 건강 데이터가 없습니다")
                    notificationManager.notify(NOTIFICATION_ID, noDataNotification)
                    delay(2000) // 2초 후 서비스 종료
                }
            }

            Log.d(TAG, "Foreground sync completed with $totalRecords records")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during foreground health data sync", e)
            
            // 오류 알림
            val errorNotification = createNotification("동기화 오류: ${e.message}")
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, errorNotification)
            delay(3000) // 3초 후 서비스 종료
        }
    }
} 