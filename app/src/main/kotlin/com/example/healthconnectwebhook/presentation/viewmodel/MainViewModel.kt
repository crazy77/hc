package com.example.healthconnectwebhook.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthconnectwebhook.data.model.HealthRecord
import com.example.healthconnectwebhook.data.preferences.UserPreferences
import com.example.healthconnectwebhook.data.repository.HealthConnectRepository
import com.example.healthconnectwebhook.data.repository.WebhookRepository
import com.example.healthconnectwebhook.domain.usecase.ScheduleHealthSyncUseCase
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
    private val userPreferences: UserPreferences,
    private val healthConnectRepository: HealthConnectRepository,
    private val webhookRepository: WebhookRepository,
    private val scheduleHealthSyncUseCase: ScheduleHealthSyncUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeUserPreferences()
        refreshPermissionStatus()
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
            userPreferences.setSyncEnabled(newState)
            
            if (newState) {
                scheduleHealthSyncUseCase.startPeriodicSync()
                _uiState.update { it.copy(syncStatus = "자동 동기화 시작됨") }
            } else {
                scheduleHealthSyncUseCase.stopPeriodicSync()
                _uiState.update { it.copy(syncStatus = "자동 동기화 중지됨") }
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
    val selectedDate: LocalDate = LocalDate.now(ZoneId.systemDefault()) // 기본값은 오늘
) 