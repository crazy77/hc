package com.fanta.healthconnect.presentation.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.fanta.healthconnect.data.model.HealthRecord
import com.fanta.healthconnect.data.preferences.UserPreferences
import com.fanta.healthconnect.data.repository.HealthConnectRepository
import com.fanta.healthconnect.data.repository.WebhookRepository
import com.fanta.healthconnect.domain.usecase.ScheduleHealthSyncUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val userPreferences: UserPreferences,
    private val healthConnectRepository: HealthConnectRepository,
    private val webhookRepository: WebhookRepository,
    private val scheduleHealthSyncUseCase: ScheduleHealthSyncUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeUserPreferences()
        refreshPermissionStatus()
        observeWorkManagerStatus()
    }

    private fun observeUserPreferences() {
        viewModelScope.launch {
            userPreferences.webhookUrl.collect { url ->
                _uiState.update { it.copy(webhookUrl = url) }
            }
        }

        viewModelScope.launch {
            userPreferences.syncInterval.collect { interval ->
                _uiState.update { it.copy(syncInterval = interval) }
            }
        }

        viewModelScope.launch {
            userPreferences.isSyncEnabled.collect { enabled ->
                _uiState.update { it.copy(isSyncEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferences.lastSyncTime.collect { time ->
                _uiState.update { 
                    it.copy(lastSyncTime = time?.let { formatInstant(it) }) 
                }
            }
        }
    }

    private fun observeWorkManagerStatus() {
        viewModelScope.launch {
            scheduleHealthSyncUseCase.getAllSyncWorkInfo().asFlow()
                .collect { workInfos: List<WorkInfo> ->
                    
                    Log.d("MainViewModel", "WorkManager status update: ${workInfos.size} work items")
                    workInfos.forEach { workInfo ->
                        Log.d("MainViewModel", "Work: ${workInfo.id}, State: ${workInfo.state}, Tags: ${workInfo.tags}")
                        Log.d("MainViewModel", "OutputData: ${workInfo.outputData.keyValueMap}")
                    }
                    
                    // 가장 최근 작업들 분석
                    val runningWork = workInfos.find { it.state == WorkInfo.State.RUNNING }
                    val enqueuedWork = workInfos.find { it.state == WorkInfo.State.ENQUEUED }
                    val succeededWorks = workInfos.filter { it.state == WorkInfo.State.SUCCEEDED }
                    val failedWorks = workInfos.filter { it.state == WorkInfo.State.FAILED }
                    val blockedWork = workInfos.find { it.state == WorkInfo.State.BLOCKED }
                    val cancelledWork = workInfos.find { it.state == WorkInfo.State.CANCELLED }
                    
                    // 가장 최근 성공/실패 작업 찾기 (finishTime이 없으면 runAttemptCount로 정렬)
                    val latestSucceeded = succeededWorks.maxWithOrNull(compareBy<WorkInfo> { 
                        it.outputData.getString("finishTime") ?: "0" 
                    }.thenBy { it.runAttemptCount })
                    
                    val latestFailed = failedWorks.maxWithOrNull(compareBy<WorkInfo> { 
                        it.outputData.getString("finishTime") ?: "0" 
                    }.thenBy { it.runAttemptCount })
                    
                    Log.d("MainViewModel", "Latest succeeded: ${latestSucceeded?.id}, Latest failed: ${latestFailed?.id}")
                    
                    // 시간 정보 추출
                    val lastSuccessTime = latestSucceeded?.outputData?.getString("finishTime")?.let { 
                        formatWorkTime(it) 
                    }
                    
                    // 실패 시간 추출 (finishTime이 없으면 대체 시간 사용)
                    val lastFailureTime = latestFailed?.let { failedWork ->
                        val finishTime = failedWork.outputData.getString("finishTime")
                        if (finishTime != null) {
                            formatWorkTime(finishTime)
                        } else {
                            // finishTime이 없으면 현재 시간에서 추정 (개발용)
                            val currentTime = Instant.now().minusSeconds((failedWork.runAttemptCount * 60).toLong())
                            formatWorkTime(currentTime.toString())
                        }
                    }
                    
                    // 실행 중이거나 대기 중인 작업의 시간은 현재 시간으로 표시
                    val lastAttemptTime = if (runningWork != null || enqueuedWork != null) {
                        formatWorkTime(Instant.now().toString())
                    } else null
                    
                    val status = when {
                        runningWork != null -> "백그라운드 동기화 실행 중"
                        enqueuedWork != null -> "백그라운드 동기화 대기 중 (다음 실행 예정)"
                        latestSucceeded != null && (latestFailed == null || latestSucceeded.runAttemptCount >= latestFailed.runAttemptCount) -> 
                            "마지막 백그라운드 동기화 성공${lastSuccessTime?.let { " ($it)" } ?: ""}"
                        latestFailed != null -> 
                            "마지막 백그라운드 동기화 실패${lastFailureTime?.let { " ($it)" } ?: ""}"
                        blockedWork != null -> "백그라운드 동기화 차단됨 (제약 조건 미충족)"
                        cancelledWork != null -> "백그라운드 동기화 취소됨"
                        workInfos.isEmpty() -> "백그라운드 동기화 미설정"
                        else -> "백그라운드 동기화 상태 알 수 없음"
                    }
                    
                    Log.d("MainViewModel", "Final status: $status")
                    Log.d("MainViewModel", "Success time: $lastSuccessTime, Failure time: $lastFailureTime")
                    
                    _uiState.update { 
                        it.copy(
                            workManagerStatus = status,
                            lastSyncSuccessTime = lastSuccessTime,
                            lastSyncFailureTime = lastFailureTime,
                            lastSyncAttemptTime = lastAttemptTime
                        ) 
                    }
                }
        }
    }
    
    private fun formatWorkTime(timeString: String): String? {
        return try {
            val instant = Instant.parse(timeString)
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
                .format(instant.atZone(ZoneId.systemDefault()))
        } catch (e: Exception) {
            null
        }
    }

    // 배터리 최적화 상태 확인
    fun checkBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = application.getSystemService(PowerManager::class.java)
            return powerManager.isIgnoringBatteryOptimizations(application.packageName)
        }
        return true
    }

    // 배터리 최적화 제외 요청
    fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(intent)
        }
    }

    // 자동 시작 관리 화면으로 이동 (제조사별)
    fun requestAutoStartPermission() {
        val intent = Intent().apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            // 제조사별 자동 시작 관리 화면
            when (Build.MANUFACTURER.lowercase()) {
                "huawei" -> {
                    component = android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                }
                "xiaomi" -> {
                    component = android.content.ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                "oppo" -> {
                    component = android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                "vivo" -> {
                    component = android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                else -> {
                    // 일반적인 설정 화면
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.parse("package:${application.packageName}")
                }
            }
        }
        
        try {
            application.startActivity(intent)
        } catch (e: Exception) {
            // 실패 시 일반 설정 화면으로
            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${application.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            application.startActivity(fallbackIntent)
        }
    }

    fun requestHealthConnectPermissions() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(syncStatus = "Health Connect 확인 중...") }
                
                // Health Connect가 사용 가능한지 확인
                if (!healthConnectRepository.isHealthConnectAvailable()) {
                    _uiState.update { 
                        it.copy(
                            isPermissionGranted = false,
                            syncStatus = "Health Connect가 설치되지 않았거나 사용할 수 없습니다. Play Store에서 Health Connect를 설치해주세요."
                        ) 
                    }
                    return@launch
                }
                
                // 권한 확인
                val isPermissionGranted = healthConnectRepository.checkPermissions()
                if (isPermissionGranted) {
                    _uiState.update { 
                        it.copy(
                            isPermissionGranted = true,
                            syncStatus = "권한이 이미 허용되어 있습니다"
                        ) 
                    }
                    // 권한이 있으면 자동으로 오늘 건강 데이터 로드
                    loadTodayHealthData()
                } else {
                    _uiState.update { 
                        it.copy(
                            isPermissionGranted = false,
                            syncStatus = "Health Connect 권한이 필요합니다. 권한 요청 버튼을 눌러주세요."
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isPermissionGranted = false,
                        syncStatus = "권한 확인 실패: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun refreshPermissionStatus() {
        viewModelScope.launch {
            try {
                if (!healthConnectRepository.isHealthConnectAvailable()) {
                    _uiState.update { 
                        it.copy(
                            isPermissionGranted = false,
                            syncStatus = "Health Connect가 설치되지 않았거나 사용할 수 없습니다."
                        ) 
                    }
                    return@launch
                }
                
                val isGranted = healthConnectRepository.checkPermissions()
                _uiState.update { 
                    it.copy(
                        isPermissionGranted = isGranted,
                        syncStatus = if (isGranted) "권한 허용됨" else "권한 필요"
                    ) 
                }
                
                // 권한이 새로 승인되었으면 오늘 건강 데이터 로드
                if (isGranted) {
                    loadTodayHealthData()
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isPermissionGranted = false,
                        syncStatus = "권한 상태 확인 실패: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun updateWebhookUrl(url: String) {
        _uiState.update { it.copy(webhookUrl = url) }
    }

    fun updateSyncInterval(interval: Int) {
        if (interval >= 15) {
            _uiState.update { it.copy(syncInterval = interval) }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val currentState = _uiState.value
            userPreferences.setWebhookUrl(currentState.webhookUrl)
            userPreferences.setSyncInterval(currentState.syncInterval)
            _uiState.update { it.copy(syncStatus = "설정 저장됨") }
        }
    }

    fun toggleSync() {
        viewModelScope.launch {
            val newState = !_uiState.value.isSyncEnabled
            Log.d("MainViewModel", "Toggling sync: $newState")
            
            try {
                userPreferences.setSyncEnabled(newState)
                
                if (newState) {
                    Log.d("MainViewModel", "Starting periodic sync...")
                    scheduleHealthSyncUseCase.startPeriodicSync()
                    _uiState.update { it.copy(syncStatus = "자동 동기화 시작됨") }
                    Log.d("MainViewModel", "Periodic sync started successfully")
                } else {
                    Log.d("MainViewModel", "Stopping periodic sync...")
                    scheduleHealthSyncUseCase.stopPeriodicSync()
                    _uiState.update { it.copy(syncStatus = "자동 동기화 중지됨") }
                    Log.d("MainViewModel", "Periodic sync stopped successfully")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling sync", e)
                _uiState.update { it.copy(syncStatus = "동기화 설정 오류: ${e.message}") }
            }
        }
    }

    // 강제로 백그라운드 동기화 트리거
    fun triggerBackgroundSync() {
        viewModelScope.launch {
            try {
                Log.d("MainViewModel", "Triggering manual background sync...")
                _uiState.update { it.copy(syncStatus = "백그라운드 동기화 수동 실행 중...") }
                
                scheduleHealthSyncUseCase.triggerManualSync()
                _uiState.update { it.copy(syncStatus = "백그라운드 동기화가 큐에 추가되었습니다") }
                
                Log.d("MainViewModel", "Background sync triggered successfully")
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error triggering background sync", e)
                _uiState.update { it.copy(syncStatus = "백그라운드 동기화 실행 실패: ${e.message}") }
            }
        }
    }

    // 선택된 날짜의 데이터를 웹훅으로 전송
    fun performManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncStatus = "동기화 중") }
            
            try {
                val webhookUrl = userPreferences.webhookUrl.first()
                val userId = userPreferences.userId.first()
                
                if (webhookUrl.isBlank()) {
                    _uiState.update { it.copy(syncStatus = "웹훅 URL을 설정해주세요") }
                    return@launch
                }
                
                if (!healthConnectRepository.checkPermissions()) {
                    _uiState.update { it.copy(syncStatus = "Health Connect 권한이 필요합니다") }
                    return@launch
                }
                
                // 선택된 날짜의 건강 데이터 가져오기
                val selectedDate = _uiState.value.selectedDate
                
                healthConnectRepository.getHealthDataForDate(selectedDate).collect { healthRecords ->
                    if (healthRecords.isNotEmpty()) {
                        // 웹훅으로 데이터 전송
                        val result = webhookRepository.sendHealthData(
                            webhookUrl = webhookUrl,
                            healthRecords = healthRecords,
                            userId = userId
                        )
                        
                        if (result.isSuccess) {
                            userPreferences.setLastSyncTime(Instant.now())
                            _uiState.update { 
                                it.copy(syncStatus = "동기화 완료 (${healthRecords.size}개 항목 전송)") 
                            }
                        } else {
                            val error = result.exceptionOrNull()
                            _uiState.update { 
                                it.copy(syncStatus = "웹훅 전송 실패: ${error?.message ?: "알 수 없는 오류"}") 
                            }
                        }
                    } else {
                        _uiState.update { it.copy(syncStatus = "전송할 건강 데이터가 없습니다") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(syncStatus = "동기화 실패: ${e.message}") }
            }
        }
    }

    // 오늘 날짜의 건강 데이터 로드 (기본값)
    fun loadTodayHealthData() {
        viewModelScope.launch {
            if (!_uiState.value.isPermissionGranted) {
                _uiState.update { it.copy(syncStatus = "권한이 필요합니다") }
                return@launch
            }

            try {
                _uiState.update { it.copy(isLoadingData = true, syncStatus = "오늘의 건강 데이터 로딩 중...") }
                
                healthConnectRepository.getTodayHealthData().collect { healthRecords ->
                    _uiState.update { 
                        it.copy(
                            healthData = healthRecords,
                            isLoadingData = false,
                            syncStatus = if (healthRecords.isNotEmpty()) 
                                "오늘의 건강 데이터 로딩 완료 (${healthRecords.size}개 항목)" 
                            else 
                                "오늘의 건강 데이터가 없습니다"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingData = false,
                        syncStatus = "건강 데이터 로딩 실패: ${e.message}"
                    ) 
                }
            }
        }
    }

    // 특정 날짜 선택
    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        loadHealthDataForSelectedDate()
    }

    // 선택된 날짜의 건강 데이터 로드
    fun loadHealthDataForSelectedDate() {
        viewModelScope.launch {
            if (!_uiState.value.isPermissionGranted) {
                _uiState.update { it.copy(syncStatus = "권한이 필요합니다") }
                return@launch
            }

            try {
                val selectedDate = _uiState.value.selectedDate
                val dateString = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                
                _uiState.update { 
                    it.copy(
                        isLoadingData = true, 
                        syncStatus = "${dateString}의 건강 데이터 로딩 중..."
                    ) 
                }
                
                healthConnectRepository.getHealthDataForDate(selectedDate).collect { healthRecords ->
                    _uiState.update { 
                        it.copy(
                            healthData = healthRecords,
                            isLoadingData = false,
                            syncStatus = if (healthRecords.isNotEmpty()) 
                                "${dateString}의 건강 데이터 로딩 완료 (${healthRecords.size}개 항목)" 
                            else 
                                "${dateString}의 건강 데이터가 없습니다"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingData = false,
                        syncStatus = "건강 데이터 로딩 실패: ${e.message}"
                    ) 
                }
            }
        }
    }

    // 기존 메서드는 호환성을 위해 유지 (7일 데이터)
    fun loadHealthData() {
        viewModelScope.launch {
            if (!_uiState.value.isPermissionGranted) {
                _uiState.update { it.copy(syncStatus = "권한이 필요합니다") }
                return@launch
            }

            try {
                _uiState.update { it.copy(isLoadingData = true, syncStatus = "건강 데이터 로딩 중...") }
                
                // 최근 7일간의 데이터 가져오기
                val since = Instant.now().minus(7, ChronoUnit.DAYS)
                
                healthConnectRepository.getHealthDataSince(since).collect { healthRecords ->
                    _uiState.update { 
                        it.copy(
                            healthData = healthRecords,
                            isLoadingData = false,
                            syncStatus = if (healthRecords.isNotEmpty()) 
                                "건강 데이터 로딩 완료 (${healthRecords.size}개 항목)" 
                            else 
                                "건강 데이터가 없습니다"
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingData = false,
                        syncStatus = "건강 데이터 로딩 실패: ${e.message}"
                    ) 
                }
            }
        }
    }

    private fun formatInstant(instant: Instant): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(instant.atZone(ZoneId.systemDefault()))
    }
}

data class MainUiState(
    val isPermissionGranted: Boolean = false,
    val webhookUrl: String = "",
    val syncInterval: Int = 15,
    val isSyncEnabled: Boolean = false,
    val syncStatus: String = "대기 중",
    val lastSyncTime: String? = null,
    val healthData: List<HealthRecord> = emptyList(),
    val isLoadingData: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(ZoneId.systemDefault()), // 기본값은 오늘
    val workManagerStatus: String = "백그라운드 동기화 미설정",
    val lastSyncSuccessTime: String? = null,
    val lastSyncFailureTime: String? = null,
    val lastSyncAttemptTime: String? = null
) 