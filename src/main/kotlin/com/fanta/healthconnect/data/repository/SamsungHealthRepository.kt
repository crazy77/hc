package com.fanta.healthconnect.data.repository

import android.content.Context
import android.renderscript.Element
import android.util.Log
import kotlin.math.round
import com.fanta.healthconnect.data.model.HealthRecord
import com.fanta.healthconnect.data.model.HealthDataType
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SamsungHealthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SamsungHealthRepository"
    
    // Samsung Health Data SDK 클래스들
    private var healthDataStore: HealthDataStore? = null
    private var healthDataService: HealthDataService? = null
    
    suspend fun connectToSamsungHealth(): Boolean {
        return try {
            Log.d(TAG, "Connecting to Samsung Health Data SDK...")
            
            // 공식 문서 예제: HealthDataService.getStore() 사용
            healthDataStore = HealthDataService.getStore(context)
            Log.d(TAG, "Samsung Health Data SDK connected successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Samsung Health Data SDK", e)
            if (e is ResolvablePlatformException && e.hasResolution) {
                Log.d(TAG, "Resolvable exception detected, may need user action")
            }
            false
        }
    }
    
    suspend fun getBloodGlucoseData(startTime: Instant, endTime: Instant): List<HealthRecord> {
        return try {
            Log.d(TAG, "Fetching blood glucose data from Samsung Health...")
            
            if (healthDataStore == null) {
                Log.w(TAG, "HealthDataStore is null, cannot fetch data")
                return emptyList()
            }
            
            // 시간 범위를 LocalDateTime으로 변환
            val startLocalDateTime = startTime.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endLocalDateTime = endTime.atZone(ZoneId.systemDefault()).toLocalDateTime()
            
            // 혈당 데이터 읽기 요청 생성
            val readRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                .setLocalTimeFilter(
                    com.samsung.android.sdk.health.data.request.LocalTimeFilter.of(
                        startLocalDateTime, 
                        endLocalDateTime
                    )
                )
                .build()
            
            // 데이터 읽기 실행
            val readResult = healthDataStore!!.readData(readRequest)
            val dataPoints = readResult.dataList
            
            Log.d(TAG, "Retrieved ${dataPoints.size} blood glucose records from Samsung Health")
            
            // 일별 집계 데이터만 생성 (기존 Health Connect 형식과 동일)
            val dailyAggregates = aggregateDailyBloodGlucose(dataPoints, LocalDate.now()) // 현재 날짜를 기본값으로 사용
            
            Log.d(TAG, "Generated ${dailyAggregates.size} daily aggregated blood glucose records")
            dailyAggregates
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch blood glucose data from Samsung Health", e)
            emptyList()
        }
    }
    
    suspend fun getBloodGlucoseDataForDate(date: LocalDate): List<HealthRecord> {
        return try {
            Log.d(TAG, "Fetching blood glucose data for date: $date")
            
            if (healthDataStore == null) {
                Log.w(TAG, "HealthDataStore is null, cannot fetch data")
                return emptyList()
            }
            
            // 더 넓은 시간 범위로 데이터 요청 (전후 7일)
            val startOfRange = date.minusDays(7).atStartOfDay(ZoneId.systemDefault()).toLocalDateTime()
            val endOfRange = date.plusDays(7).atStartOfDay(ZoneId.systemDefault()).toLocalDateTime()
            
            Log.d(TAG, "Requesting data for wider time range: $startOfRange to $endOfRange")
            
            // 혈당 데이터 읽기 요청 생성
            val readRequest = DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder
                .setLocalTimeFilter(
                    com.samsung.android.sdk.health.data.request.LocalTimeFilter.of(
                        startOfRange, 
                        endOfRange
                    )
                )
                .build()
            
            // 데이터 읽기 실행
            val readResult = healthDataStore!!.readData(readRequest)
            val dataPoints = readResult.dataList
            
            Log.d(TAG, "Retrieved ${dataPoints.size} blood glucose records for wider time range")
            
            // 각 데이터 포인트의 시간 정보 로깅
            for (i in dataPoints.indices) {
                val dataPoint = dataPoints[i]
                val localDateTime = dataPoint.startTime.atZone(ZoneId.systemDefault()).toLocalDateTime()
                val localDate = localDateTime.toLocalDate()
                val glucoseLevel = dataPoint.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) as? Float
                val measureType = dataPoint.getValue(DataType.BloodGlucoseType.MEAL_STATUS)
                
                Log.d(TAG, "Data point $i: Level=${(glucoseLevel ?: 0f) * 18} Type=$measureType")
            }
            
            // 일별 집계 데이터만 생성 (기존 Health Connect 형식과 동일)
            val dailyAggregates = aggregateDailyBloodGlucose(dataPoints, date)
            
            Log.d(TAG, "Generated ${dailyAggregates.size} daily aggregated blood glucose records for date $date")
            dailyAggregates
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch blood glucose data for date $date", e)
            emptyList()
        }
    }
    
    private fun aggregateDailyBloodGlucose(
        dataPoints: List<HealthDataPoint>,
        targetDate: LocalDate
    ): List<HealthRecord> {
        // 1. 날짜 필터링: targetDate만 남김
        val filteredPoints = dataPoints.filter { dataPoint ->
            val localDate = dataPoint.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            localDate == targetDate
        }
        val filteredFasting = filteredPoints.filter { dataPoint ->
            val type= dataPoint.getValue( DataType.BloodGlucoseType.MEAL_STATUS)
            type == DataType.BloodGlucoseType.MealStatus.FASTING;
        }
        if (filteredPoints.isEmpty()) return emptyList()

        val glucoseLevels = filteredPoints.mapNotNull {
            it.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
        }
        if (glucoseLevels.isEmpty()) return emptyList()

        
        val avgGlucose = glucoseLevels.average().toFloat()
        val maxGlucose = glucoseLevels.maxOrNull()!!
        val minGlucose = glucoseLevels.minOrNull()!!
        var fastingGlucose: Float? = null
        if (filteredFasting.isEmpty()) {
            fastingGlucose = avgGlucose
        } else {
            fastingGlucose = filteredFasting[0].getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)?.toFloat()
            if (fastingGlucose == null) {
                fastingGlucose = avgGlucose
            }
        }

        val fastingGlucoseMgdl= round(fastingGlucose * 18).toInt()
        val avgGlucoseMgdl = round(avgGlucose * 18).toInt()
        val maxGlucoseMgdl = round(maxGlucose * 18).toInt()
        val minGlucoseMgdl = round(minGlucose * 18).toInt()

        return listOf(
            HealthRecord(
                type = HealthDataType.BLOOD_GLUCOSE,
                value = fastingGlucoseMgdl.toString(),
                unit = "mg/dL",
                recordTime = targetDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toString(),
                metadata = mapOf("date" to targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            ),
            HealthRecord(
                type = HealthDataType.BLOOD_GLUCOSE_AVG,
                value = avgGlucoseMgdl.toString(),
                unit = "mg/dL",
                recordTime = targetDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toString(),
                metadata = mapOf("date" to targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            ),
            HealthRecord(
                type = HealthDataType.BLOOD_GLUCOSE_MAX,
                value = maxGlucoseMgdl.toString(),
                unit = "mg/dL",
                recordTime = targetDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toString(),
                metadata = mapOf("date" to targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            ),
            HealthRecord(
                type = HealthDataType.BLOOD_GLUCOSE_MIN,
                value = minGlucoseMgdl.toString(),
                unit = "mg/dL",
                recordTime = targetDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant().toString(),
                metadata = mapOf("date" to targetDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            )
        )
    }
    
    suspend fun requestPermissions(): Boolean {
        return try {
            Log.d(TAG, "Requesting Samsung Health Data SDK permissions...")
            
            if (healthDataStore == null) {
                Log.w(TAG, "HealthDataStore is null, cannot request permissions")
                return false
            }
            
            // 공식 문서 예제: 혈당 데이터 읽기 권한 요청
            val permissionSet = setOf(
                Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
            )
            
            val grantedPermissions = healthDataStore!!.getGrantedPermissions(permissionSet)
            
            if (grantedPermissions.containsAll(permissionSet)) {
                Log.d(TAG, "All required permissions already granted")
                return true
            } else {
                Log.d(TAG, "Requesting permissions from user...")
                // 실제 권한 요청은 Activity에서 처리해야 함
                // 여기서는 권한 상태만 확인
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Samsung Health Data SDK permissions", e)
            false
        }
    }
    
    suspend fun requestPermissionsWithActivity(activity: android.app.Activity): Boolean {
        return try {
            Log.d(TAG, "Requesting Samsung Health Data SDK permissions with Activity...")
            
            if (healthDataStore == null) {
                Log.w(TAG, "HealthDataStore is null, cannot request permissions")
                return false
            }
            
            // 공식 문서 예제: 혈당 데이터 읽기 권한 요청
            val permissionSet = setOf(
                Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)
            )
            
            val grantedPermissions = healthDataStore!!.getGrantedPermissions(permissionSet)
            
            if (grantedPermissions.containsAll(permissionSet)) {
                Log.d(TAG, "All required permissions already granted")
                return true
            } else {
                Log.d(TAG, "Requesting permissions from user with Activity...")
                
                // SDK의 실제 권한 요청 메서드 사용
                val resultPermissions = healthDataStore!!.requestPermissions(permissionSet, activity)
                
                Log.d(TAG, "Permission request result: $resultPermissions")
                
                // 권한 요청 결과 확인
                val hasAllPermissions = resultPermissions.containsAll(permissionSet)
                Log.d(TAG, "All permissions granted: $hasAllPermissions")
                
                return hasAllPermissions
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Samsung Health Data SDK permissions with Activity", e)
            false
        }
    }
    
    fun isConnected(): Boolean {
        return healthDataStore != null
    }
    
    fun disconnect() {
        try {
            Log.d(TAG, "Disconnecting from Samsung Health Data SDK...")
            healthDataStore = null
            healthDataService = null
            Log.d(TAG, "Disconnected from Samsung Health Data SDK")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from Samsung Health Data SDK", e)
        }
    }


} 