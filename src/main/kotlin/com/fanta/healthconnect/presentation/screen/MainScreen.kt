package com.fanta.healthconnect.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fanta.healthconnect.data.model.HealthDataType
import com.fanta.healthconnect.data.model.HealthRecord
import com.fanta.healthconnect.presentation.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onRequestPermissions: (() -> Unit)? = null,
    onRequestSamsungHealthPermissions: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 날짜 선택 카드
        DateSelectionCard(
            selectedDate = uiState.selectedDate,
            onDateSelected = { viewModel.selectDate(it) }
        )
        // 동기화 제어 카드
        SyncControlCard(
            isSyncEnabled = uiState.isSyncEnabled,
            syncStatus = uiState.syncStatus,
            lastSyncTime = uiState.lastSyncTime,
            selectedDate = uiState.selectedDate,
            onToggleSync = { viewModel.toggleBackgroundSync() },
            onManualSync = { viewModel.performManualSync() }
        )
        // 건강 데이터 표시 카드
        HealthDataCard(
            healthData = uiState.healthData,
            isLoading = uiState.isLoadingData,
            isPermissionGranted = uiState.isPermissionGranted,
            selectedDate = uiState.selectedDate,
            onLoadData = { viewModel.loadHealthDataForSelectedDate() },
            onLoadTodayData = { viewModel.loadTodayHealthData() }
        )
        // Health Connect 권한 상태 카드
        PermissionStatusCard(
            isPermissionGranted = uiState.isPermissionGranted,
            onRequestPermissions = { 
                android.util.Log.d("MainScreen", "권한 요청 버튼 클릭됨")
                android.util.Log.d("MainScreen", "onRequestPermissions null 여부: ${onRequestPermissions == null}")
                onRequestPermissions?.invoke() ?: viewModel.requestHealthConnectPermissions()
            },
            onRefreshPermissions = { viewModel.refreshPermissionStatus() }
        )
        // Samsung Health 권한 상태 카드
        SamsungHealthPermissionCard(
            onRequestSamsungHealthPermissions = {
                android.util.Log.d("MainScreen", "Samsung Health 권한 요청 버튼 클릭됨")
                onRequestSamsungHealthPermissions?.invoke()
            }
        )
        // 설정 카드
        SettingsCard(
            webhookUrl = uiState.webhookUrl,
            syncInterval = uiState.syncInterval,
            onWebhookUrlChange = { viewModel.updateWebhookUrl(it) },
            onSyncIntervalChange = { viewModel.updateSyncInterval(it) },
            onSaveSettings = { viewModel.saveSettings() }
        )    
        // 백그라운드 최적화 카드
        BackgroundOptimizationCard(
            workManagerStatus = uiState.workManagerStatus,
            lastSyncSuccessTime = uiState.lastSyncSuccessTime,
            lastSyncFailureTime = uiState.lastSyncFailureTime,
            onToggleBackgroundSync = { viewModel.toggleBackgroundSync() },
            onTriggerBackgroundSync = { viewModel.performManualSync() },
            onForceRestartWorkManager = { viewModel.forceRestartWorkManager() },
            onDiagnosisBackgroundRestrictions = { viewModel.diagnosisBackgroundRestrictions() },
            onShowBackgroundOptimizationGuide = { viewModel.showBackgroundOptimizationGuide() },
            onRequestBatteryOptimizationExemption = { viewModel.requestBatteryOptimizationExemption() },
            onRequestAutoStartPermission = { viewModel.requestAutoStartPermission() },
            isPermissionGranted = uiState.isPermissionGranted,
            onLoadTodayData = { viewModel.loadTodayHealthData() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSelectionCard(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "날짜 선택",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "선택된 날짜",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", java.util.Locale.KOREAN)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedButton(
                    onClick = { showDatePicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "날짜 선택"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("날짜 변경")
                }
            }

            // 빠른 선택 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val today = LocalDate.now(ZoneId.systemDefault())
                
                OutlinedButton(
                    onClick = { onDateSelected(today) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDate != today
                ) {
                    Text("오늘")
                }
                
                OutlinedButton(
                    onClick = { onDateSelected(today.minusDays(1)) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDate != today.minusDays(1)
                ) {
                    Text("어제")
                }
                
                OutlinedButton(
                    onClick = { onDateSelected(today.minusDays(2)) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDate != today.minusDays(2)
                ) {
                    Text("2일전")
                }
            }
        }
    }

    // 날짜 선택 다이얼로그
    if (showDatePicker) {
        DatePickerDialog(
            onDateSelected = { dateMillis ->
                dateMillis?.let {
                    val localDate = LocalDate.ofEpochDay(it / (24 * 60 * 60 * 1000))
                    onDateSelected(localDate)
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onDateSelected(datePickerState.selectedDateMillis) }
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun PermissionStatusCard(
    isPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    var isRequestingPermission by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPermissionGranted) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Health Connect 권한",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when {
                    isRequestingPermission -> "권한 요청 중..."
                    isPermissionGranted -> "권한 승인됨"
                    else -> "권한 필요"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPermissionGranted) {
                    android.util.Log.d("MainScreen", "권한 요청 버튼 표시됨 (isPermissionGranted: $isPermissionGranted)")
                    Button(
                        onClick = {
                            android.util.Log.d("MainScreen", "권한 요청 버튼 클릭됨 (PermissionStatusCard)")
                            isRequestingPermission = true
                            onRequestPermissions()
                            // 3초 후에 요청 상태를 리셋 (권한 다이얼로그가 나타나지 않는 경우를 대비)
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(3000)
                                isRequestingPermission = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isRequestingPermission
                    ) {
                        if (isRequestingPermission) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("요청 중...")
                        } else {
                            Text("권한 요청")
                        }
                    }
                } else {
                    android.util.Log.d("MainScreen", "권한 요청 버튼 숨김됨 (isPermissionGranted: $isPermissionGranted)")
                }
                
                OutlinedButton(
                    onClick = {
                        onRefreshPermissions()
                        isRequestingPermission = false
                    },
                    enabled = !isRequestingPermission
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "새로고침"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("새로고침")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    webhookUrl: String,
    syncInterval: Int,
    onWebhookUrlChange: (String) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onSaveSettings: () -> Unit
) {
    var localWebhookUrl by remember(webhookUrl) { mutableStateOf(webhookUrl) }
    var localSyncInterval by remember(syncInterval) { mutableStateOf(syncInterval.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = localWebhookUrl,
                onValueChange = { localWebhookUrl = it },
                label = { Text("웹훅 URL") },
                placeholder = { Text("https://your-webhook-url.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = localSyncInterval,
                onValueChange = { localSyncInterval = it },
                label = { Text("동기화 간격 (분)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            Button(
                onClick = {
                    onWebhookUrlChange(localWebhookUrl)
                    localSyncInterval.toIntOrNull()?.let { onSyncIntervalChange(it) }
                    onSaveSettings()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("설정 저장")
            }
        }
    }
}

@Composable
private fun SyncControlCard(
    isSyncEnabled: Boolean,
    syncStatus: String,
    lastSyncTime: String?,
    selectedDate: LocalDate,
    onToggleSync: () -> Unit,
    onManualSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "동기화 제어",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "자동 동기화",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = isSyncEnabled,
                    onCheckedChange = { onToggleSync() }
                )
            }

            Text(
                text = "상태: $syncStatus",
                style = MaterialTheme.typography.bodySmall
            )

            lastSyncTime?.let {
                Text(
                    text = "마지막 동기화: $it",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val isToday = selectedDate == LocalDate.now(ZoneId.systemDefault())
            val buttonText = if (isToday) {
                "오늘 데이터 동기화"
            } else {
                "선택된 날짜 동기화 (${selectedDate.format(DateTimeFormatter.ofPattern("MM/dd"))})"
            }

            Button(
                onClick = onManualSync,
                modifier = Modifier.fillMaxWidth(),
                enabled = syncStatus != "동기화 중"
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun HealthDataCard(
    healthData: List<HealthRecord>,
    isLoading: Boolean,
    isPermissionGranted: Boolean,
    selectedDate: LocalDate,
    onLoadData: () -> Unit,
    onLoadTodayData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isToday = selectedDate == LocalDate.now(ZoneId.systemDefault())
            val titleText = if (isToday) {
                "오늘의 건강 데이터"
            } else {
                "${selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}의 건강 데이터"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 빠른 액션 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadData,
                    enabled = isPermissionGranted && !isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("로딩중")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "데이터 로드"
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("새로고침")
                    }
                }

                if (!isToday) {
                    OutlinedButton(
                        onClick = onLoadTodayData,
                        enabled = isPermissionGranted && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("오늘로 이동")
                    }
                }
            }

            if (!isPermissionGranted) {
                Text(
                    text = "Health Connect 권한이 필요합니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (healthData.isEmpty() && !isLoading) {
                Text(
                    text = "건강 데이터가 없습니다. '새로고침' 버튼을 눌러주세요.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                // 데이터 타입별로 그룹화하고 최신순으로 정렬
                val groupedData = healthData
                    .sortedByDescending { it.recordTime } // 전체 데이터를 최신순으로 정렬
                    .groupBy { it.type }
                    .mapValues { (_, records) -> 
                        records.sortedByDescending { it.recordTime } // 각 그룹 내에서도 최신순 정렬
                    }
                
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupedData.entries.toList()) { (dataType, records) ->
                        HealthDataTypeItem(
                            dataType = dataType,
                            records = records
                        )
                    }
                }
                
                if (healthData.isNotEmpty()) {
                    Text(
                        text = "총 ${healthData.size}개 항목",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthDataTypeItem(
    dataType: HealthDataType,
    records: List<HealthRecord>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = getDataTypeDisplayName(dataType),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = "${records.size}개 기록",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            // 최신 데이터 몇 개만 표시
            records.take(3).forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatValueWithUnit(record),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatRecordTime(record.recordTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (records.size > 3) {
                Text(
                    text = "... 외 ${records.size - 3}개 더",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getDataTypeDisplayName(dataType: HealthDataType): String {
    return when (dataType) {
        HealthDataType.STEPS -> "걸음수 (일일 합계)"
        HealthDataType.HEART_RATE -> "심박수"
        HealthDataType.HEART_RATE_AVERAGE -> "심박수 (평균)"
        HealthDataType.HEART_RATE_MAX -> "심박수 (최고)"
        HealthDataType.HEART_RATE_MIN -> "심박수 (최저)"
        HealthDataType.WEIGHT -> "체중 (일일 최저)"
        HealthDataType.SLEEP_TOTAL -> "총 수면시간"
        HealthDataType.SLEEP_DEEP -> "깊은 수면"
        HealthDataType.SLEEP_LIGHT -> "얕은 수면"
        HealthDataType.SLEEP_REM -> "렘 수면"
        HealthDataType.EXERCISE -> "운동"
        HealthDataType.BLOOD_PRESSURE -> "혈압"
        HealthDataType.CALORIES -> "칼로리 (일일 합계)"
        HealthDataType.BLOOD_GLUCOSE -> "혈당"
        HealthDataType.BLOOD_GLUCOSE_MAX -> "혈당 (최고)"
        HealthDataType.BLOOD_GLUCOSE_MIN -> "혈당 (최저)"
        HealthDataType.BODY_FAT -> "체지방"
        HealthDataType.MUSCLE_MASS -> "근육량"
        HealthDataType.BODY_WATER -> "체수분량"
        HealthDataType.BONE_MASS -> "골량"
    }
}

private fun formatValueWithUnit(record: HealthRecord): String {
    return when (record.type) {
        HealthDataType.SLEEP_TOTAL,
        HealthDataType.SLEEP_DEEP,
        HealthDataType.SLEEP_LIGHT,
        HealthDataType.SLEEP_REM -> {
            // 분을 시간:분 형태로 변환
            val minutes = record.value.toIntOrNull() ?: 0
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            "${hours}시간 ${remainingMinutes}분"
        }
        else -> "${record.value} ${record.unit}"
    }
}

private fun formatRecordTime(recordTime: String): String {
    return try {
        val instant = java.time.Instant.parse(recordTime)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd")
        formatter.format(instant.atZone(java.time.ZoneId.systemDefault()))
    } catch (e: Exception) {
        recordTime.take(10) // 실패 시 앞 10자리만 표시
    }
}

@Composable
private fun BackgroundOptimizationCard(
    workManagerStatus: String,
    lastSyncSuccessTime: String?,
    lastSyncFailureTime: String?,
    onToggleBackgroundSync: () -> Unit,
    onTriggerBackgroundSync: () -> Unit,
    onForceRestartWorkManager: () -> Unit,
    onDiagnosisBackgroundRestrictions: () -> Unit,
    onShowBackgroundOptimizationGuide: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onRequestAutoStartPermission: () -> Unit,
    isPermissionGranted: Boolean,
    onLoadTodayData: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "백그라운드 동기화 관리",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = workManagerStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    workManagerStatus.contains("성공") -> MaterialTheme.colorScheme.primary
                    workManagerStatus.contains("실패") -> MaterialTheme.colorScheme.error
                    workManagerStatus.contains("실행 중") -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            // 마지막 성공/실패 시간 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                lastSyncSuccessTime?.let {
                    Text(
                        text = "✅ 성공: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                lastSyncFailureTime?.let {
                    Text(
                        text = "❌ 실패: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 기본 제어 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerBackgroundSync,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "백그라운드 동기화 실행"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("수동 실행")
                }

                OutlinedButton(
                    onClick = onLoadTodayData,
                    enabled = isPermissionGranted,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "데이터 확인"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("데이터 확인")
                }
            }

            // 진단 및 최적화 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDiagnosisBackgroundRestrictions,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "진단"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("진단")
                }

                OutlinedButton(
                    onClick = onShowBackgroundOptimizationGuide,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "가이드"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("가이드")
                }
            }

            // 시스템 설정 바로가기 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestBatteryOptimizationExemption,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "배터리 최적화"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("배터리 설정")
                }

                OutlinedButton(
                    onClick = onRequestAutoStartPermission,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "자동 시작"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("자동 시작")
                }
            }

            // 고급 기능 (문제 해결)
            OutlinedButton(
                onClick = onForceRestartWorkManager,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "WorkManager 재시작"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("WorkManager 재시작 (문제 해결시)")
            }
        }
    }
} 

@Composable
private fun SamsungHealthPermissionCard(
    onRequestSamsungHealthPermissions: () -> Unit
) {
    var isRequestingPermission by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Samsung Health 권한",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "혈당 데이터를 읽기 위해 Samsung Health 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        android.util.Log.d("MainScreen", "Samsung Health 권한 요청 버튼 클릭됨 (SamsungHealthPermissionCard)")
                        isRequestingPermission = true
                        onRequestSamsungHealthPermissions()
                        // 3초 후에 요청 상태를 리셋
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(3000)
                            isRequestingPermission = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isRequestingPermission
                ) {
                    if (isRequestingPermission) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("요청 중...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Samsung Health 권한 요청"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Samsung Health 권한 요청")
                    }
                }
            }
            
            Text(
                text = "참고: Samsung Health 앱에서 개발자 모드를 활성화해야 할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
} 