package com.fanta.healthconnect.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fanta.healthconnect.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val samsungHealthRepository: SamsungHealthRepository
) {

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(BoneMassRecord::class)
    )

    suspend fun isHealthConnectAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkPermissions(): Boolean {
        return try {
            if (!isHealthConnectAvailable()) {
                Log.w("HealthConnectRepository", "Health Connect not available during permission check")
                return false
            }
            
            Log.d("HealthConnectRepository", "Checking Health Connect permissions...")
            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            Log.d("HealthConnectRepository", "Granted permissions count: ${grantedPermissions.size}")
            Log.d("HealthConnectRepository", "Required permissions count: ${permissions.size}")
            
            val missingPermissions = permissions.filter { it !in grantedPermissions }
            if (missingPermissions.isNotEmpty()) {
                Log.w("HealthConnectRepository", "Missing permissions: ${missingPermissions.map { it.toString() }}")
            }
            
            val hasAllPermissions = permissions.all { it in grantedPermissions }
            Log.d("HealthConnectRepository", "Has all permissions: $hasAllPermissions")
            
            hasAllPermissions
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error checking permissions", e)
            false
        }
    }

    suspend fun getHealthDataSince(since: Instant): Flow<List<HealthRecord>> = flow {
        val timeRangeFilter = TimeRangeFilter.between(since, Instant.now())
        val healthRecords = mutableListOf<HealthRecord>()

        try {
            // 일자별로 집계된 데이터 생성
            val aggregatedData = aggregateHealthDataByDate(timeRangeFilter)
            healthRecords.addAll(aggregatedData)

            emit(healthRecords)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    // 오늘 날짜의 건강 데이터만 조회
    suspend fun getTodayHealthData(): Flow<List<HealthRecord>> = flow {
        val today = LocalDate.now(ZoneId.systemDefault())
        val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
        val healthRecords = mutableListOf<HealthRecord>()

        try {
            val aggregatedData = aggregateHealthDataByDate(timeRangeFilter)
            healthRecords.addAll(aggregatedData)
            emit(healthRecords)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    // 특정 날짜의 건강 데이터 조회
    suspend fun getHealthDataForDate(date: LocalDate): Flow<List<HealthRecord>> = flow {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
        val healthRecords = mutableListOf<HealthRecord>()

        try {
            // Health Connect 데이터 (혈당 제외)
            val aggregatedData = aggregateHealthDataByDate(timeRangeFilter)
            healthRecords.addAll(aggregatedData)
            
            // Samsung Health 혈당 데이터 (별도 처리)
            val bloodGlucoseData = getBloodGlucoseFromSamsungHealth(date)
            healthRecords.addAll(bloodGlucoseData)
            
            emit(healthRecords)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    // 특정 날짜 범위의 건강 데이터 조회
    suspend fun getHealthDataForDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<HealthRecord>> = flow {
        val startOfRange = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfRange = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val timeRangeFilter = TimeRangeFilter.between(startOfRange, endOfRange)
        val healthRecords = mutableListOf<HealthRecord>()

        try {
            val aggregatedData = aggregateHealthDataByDate(timeRangeFilter)
            healthRecords.addAll(aggregatedData)
            emit(healthRecords)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    private suspend fun aggregateHealthDataByDate(timeRange: TimeRangeFilter): List<HealthRecord> {
        val aggregatedRecords = mutableListOf<HealthRecord>()
        
        try {
            Log.d("HealthConnectRepository", "Starting data aggregation for time range: ${timeRange}")
            
            // 일자별 걸음수 집계 (합계)
            Log.d("HealthConnectRepository", "Fetching daily steps...")
            val dailySteps = aggregateDailySteps(timeRange)
            aggregatedRecords.addAll(dailySteps)
            Log.d("HealthConnectRepository", "Daily steps: ${dailySteps.size} records")

            // 일자별 칼로리 집계 (합계) 
            Log.d("HealthConnectRepository", "Fetching daily calories...")
            val dailyCalories = aggregateDailyCalories(timeRange)
            aggregatedRecords.addAll(dailyCalories)
            Log.d("HealthConnectRepository", "Daily calories: ${dailyCalories.size} records")

            // 일자별 체중 (최저값)
            Log.d("HealthConnectRepository", "Fetching daily weight...")
            val dailyWeight = aggregateDailyWeight(timeRange)
            aggregatedRecords.addAll(dailyWeight)
            Log.d("HealthConnectRepository", "Daily weight: ${dailyWeight.size} records")

            // 일자별 수면 데이터 (상세 분석)
            Log.d("HealthConnectRepository", "Fetching daily sleep...")
            val dailySleep = aggregateDailySleep(timeRange)
            aggregatedRecords.addAll(dailySleep)
            Log.d("HealthConnectRepository", "Daily sleep: ${dailySleep.size} records")

            // 심박수 집계 데이터 (평균, 최고, 최저)
            Log.d("HealthConnectRepository", "Fetching aggregated heart rate...")
            val aggregatedHeartRate = aggregateDailyHeartRate(timeRange)
            aggregatedRecords.addAll(aggregatedHeartRate)
            Log.d("HealthConnectRepository", "Aggregated heart rate: ${aggregatedHeartRate.size} records")

            // 체지방 데이터 (최저값)
            Log.d("HealthConnectRepository", "Fetching body fat...")
            val bodyFatData = aggregateDailyBodyFat(timeRange)
            aggregatedRecords.addAll(bodyFatData)
            Log.d("HealthConnectRepository", "Body fat: ${bodyFatData.size} records")

            // 근육량 데이터 (최저값)
            Log.d("HealthConnectRepository", "Fetching muscle mass...")
            val muscleMassData = aggregateDailyMuscleMass(timeRange)
            aggregatedRecords.addAll(muscleMassData)
            Log.d("HealthConnectRepository", "Muscle mass: ${muscleMassData.size} records")

            // 체수분량 데이터 (최저값)
            Log.d("HealthConnectRepository", "Fetching body water...")
            val bodyWaterData = aggregateDailyBodyWater(timeRange)
            aggregatedRecords.addAll(bodyWaterData)
            Log.d("HealthConnectRepository", "Body water: ${bodyWaterData.size} records")

            // 골량 데이터 (최저값)
            Log.d("HealthConnectRepository", "Fetching bone mass...")
            val boneMassData = aggregateDailyBoneMass(timeRange)
            aggregatedRecords.addAll(boneMassData)
            Log.d("HealthConnectRepository", "Bone mass: ${boneMassData.size} records")

            // 운동 데이터
            Log.d("HealthConnectRepository", "Fetching exercise data...")
            val exerciseData = readExerciseData(timeRange)
            aggregatedRecords.addAll(exerciseData)
            Log.d("HealthConnectRepository", "Exercise: ${exerciseData.size} records")

            // 혈압 데이터
            Log.d("HealthConnectRepository", "Fetching blood pressure...")
            val bloodPressureData = readBloodPressureData(timeRange)
            aggregatedRecords.addAll(bloodPressureData)
            Log.d("HealthConnectRepository", "Blood pressure: ${bloodPressureData.size} records")

            Log.d("HealthConnectRepository", "Total aggregated records: ${aggregatedRecords.size}")

        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error in aggregateHealthDataByDate", e)
        }

        return aggregatedRecords
    }

    private suspend fun aggregateDailySteps(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            Log.d("HealthConnectRepository", "Reading steps records for time range...")
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            Log.d("HealthConnectRepository", "Steps query result: ${response.records.size} raw records")
            
            // 일자별 걸음수 합계 계산
            val dailySteps = response.records
                .groupBy { getDateString(it.startTime) }
                .map { (date, records) ->
                    val totalSteps = records.sumOf { it.count }
                    Log.d("HealthConnectRepository", "Steps for $date: $totalSteps steps from ${records.size} records")
                    HealthRecord(
                        type = HealthDataType.STEPS,
                        value = totalSteps.toString(),
                        unit = "steps",
                        recordTime = "${date}T00:00:00Z",
                        metadata = mapOf(
                            "date" to date,
                            "recordCount" to records.size.toString()
                        )
                    )
                }
            
            Log.d("HealthConnectRepository", "Aggregated steps: ${dailySteps.size} daily records")
            dailySteps
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error reading steps data", e)
            emptyList()
        }
    }

    private suspend fun aggregateDailyCalories(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 칼로리 합계 계산
            val dailyCalories = response.records
                .groupBy { getDateString(it.startTime) }
                .map { (date, records) ->
                    val totalCalories = records.sumOf { it.energy.inCalories }
                    HealthRecord(
                        type = HealthDataType.CALORIES,
                        value = (totalCalories / 1000).toInt().toString(), // 정수로 변환
                        unit = "kcal",
                        recordTime = "${date}T00:00:00Z",
                        metadata = mapOf(
                            "date" to date,
                            "recordCount" to records.size.toString()
                        )
                    )
                }
            
            dailyCalories
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailyWeight(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 최저 체중 계산
            val dailyWeight = response.records
                .groupBy { getDateString(it.time) }
                .mapNotNull { (date, records) ->
                    val minWeight = records.minByOrNull { it.weight.inKilograms }
                    minWeight?.let {
                        HealthRecord(
                            type = HealthDataType.WEIGHT,
                            value = String.format("%.1f", it.weight.inKilograms),
                            unit = "kg",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "recordCount" to records.size.toString(),
                                "minValue" to "true"
                            )
                        )
                    }
                }
            
            dailyWeight
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailySleep(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            // 수면 세션 데이터 가져오기
            val sessionRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange
            )
            val sessionResponse = healthConnectClient.readRecords(sessionRequest)
            
            // 중복 세션 제거 (startTime, endTime이 동일한 세션들)
            val uniqueSessions = sessionResponse.records
                .distinctBy { "${it.startTime}_${it.endTime}" }
                .also { 
                    Log.d("SleepAggregation", "전체 세션: ${sessionResponse.records.size}개, 중복 제거 후: ${it.size}개")
                }
            
            // 날짜별로 수면 데이터 집계 (한국 시간대 기준)
            val dateToSessions = mutableMapOf<String, MutableList<SleepSessionRecord>>()
            
            // 각 세션을 날짜별로 분할하여 할당
            uniqueSessions.forEach { session ->
                val sessionDates = getSessionDates(session)
                sessionDates.forEach { date ->
                    dateToSessions.getOrPut(date) { mutableListOf() }.add(session)
                }
            }
            
            // 각 날짜별로 수면 데이터 계산
            val sleepRecords = mutableListOf<HealthRecord>()
            
            dateToSessions.forEach { (date, sessions) ->
                Log.d("SleepAggregation", "날짜 $date: ${sessions.size}개 세션 처리")
                
                var deepSleepMinutes = 0L
                var lightSleepMinutes = 0L
                var remSleepMinutes = 0L
                var hasStagesData = false
                var totalSessionMinutes = 0L
                
                sessions.forEachIndexed { sessionIndex, session ->
                    // 해당 날짜에 해당하는 세션 부분만 계산
                    val (sessionStart, sessionEnd) = getSessionTimeForDate(session, date)
                    val sessionDuration = ChronoUnit.MINUTES.between(sessionStart, sessionEnd)
                    totalSessionMinutes += sessionDuration
                    
                    Log.d("SleepAggregation", "수면 세션 $sessionIndex (날짜 $date 부분): $sessionStart ~ $sessionEnd (${sessionDuration}분)")
                    
                    session.stages?.let { stages ->
                        if (stages.isNotEmpty()) {
                            hasStagesData = true
                            Log.d("SleepAggregation", "수면 세션 ${sessionIndex}에 ${stages.size}개 단계 발견")
                            
                            stages.forEachIndexed { stageIndex, stage ->
                                // 단계가 해당 날짜에 포함되는 부분만 계산
                                val stageIntersection = getStageTimeForDate(stage, date)
                                if (stageIntersection != null) {
                                    val stageMinutes = ChronoUnit.MINUTES.between(stageIntersection.first, stageIntersection.second)
                                   // Log.d("SleepAggregation", "  단계 $stageIndex: ${stage.stage} ${stageIntersection.first} ~ ${stageIntersection.second} (${stageMinutes}분)")
                                    
                                    when (stage.stage) {
                                        SleepSessionRecord.STAGE_TYPE_DEEP -> {
                                            deepSleepMinutes += stageMinutes
                                           // Log.d("SleepAggregation", "  깊은 수면 +${stageMinutes}분, 총합: ${deepSleepMinutes}분")
                                        }
                                        SleepSessionRecord.STAGE_TYPE_LIGHT -> {
                                            lightSleepMinutes += stageMinutes
                                           // Log.d("SleepAggregation", "  얕은 수면 +${stageMinutes}분, 총합: ${lightSleepMinutes}분")
                                        }
                                        SleepSessionRecord.STAGE_TYPE_REM -> {
                                            remSleepMinutes += stageMinutes
                                           // Log.d("SleepAggregation", "  REM 수면 +${stageMinutes}분, 총합: ${remSleepMinutes}분")
                                        }
                                        else -> {
                                            //Log.d("SleepAggregation", "  기타 단계 (${stage.stage}) ${stageMinutes}분 - 제외")
                                        }
                                    }
                                }
                            }
                        } else {
                           // Log.d("SleepAggregation", "세션 ${sessionIndex}에 단계 데이터 없음")
                        }
                    } ?: run {
                        Log.d("SleepAggregation", "세션 ${sessionIndex}에 stages가 null")
                    }
                }
                
                // 총 수면시간 계산
                val stagesSumMinutes = deepSleepMinutes + lightSleepMinutes + remSleepMinutes
                
                Log.d("SleepAggregation", "날짜 $date 결과:")
                Log.d("SleepAggregation", "  세션 총 시간: ${totalSessionMinutes}분")
                Log.d("SleepAggregation", "  단계별 합계: ${stagesSumMinutes}분 (깊은:${deepSleepMinutes}, 얕은:${lightSleepMinutes}, REM:${remSleepMinutes})")
                Log.d("SleepAggregation", "  단계 데이터 있음: $hasStagesData")
                
                val totalSleepMinutes = if (hasStagesData && stagesSumMinutes > 0) {
                    // 단계별 데이터가 있고 0보다 클 때, 세션 시간과 비교해서 더 작은 값 사용
                    val result = minOf(totalSessionMinutes, stagesSumMinutes)
                    Log.d("SleepAggregation", "  최종 선택: ${result}분 (min of session and stages)")
                    result
                } else {
                    // 단계별 데이터가 없으면 세션 전체 시간 사용
                    Log.d("SleepAggregation", "  최종 선택: ${totalSessionMinutes}분 (session only)")
                    totalSessionMinutes
                }
                
                // 총 수면시간이 0보다 클 때만 기록 추가
                if (totalSleepMinutes > 0) {
                    sleepRecords.add(
                        HealthRecord(
                            type = HealthDataType.SLEEP_TOTAL,
                            value = totalSleepMinutes.toString(),
                            unit = "minutes",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "sessionCount" to sessions.size.toString(),
                                "hasStagesData" to hasStagesData.toString(),
                                "calculationMethod" to if (hasStagesData) "stages" else "session"
                            )
                        )
                    )
                }
                
                // 깊은 수면 기록
                if (deepSleepMinutes > 0) {
                    sleepRecords.add(
                        HealthRecord(
                            type = HealthDataType.SLEEP_DEEP,
                            value = deepSleepMinutes.toString(),
                            unit = "minutes",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "fromStages" to "true"
                            )
                        )
                    )
                }
                
                // 얕은 수면 기록
                if (lightSleepMinutes > 0) {
                    sleepRecords.add(
                        HealthRecord(
                            type = HealthDataType.SLEEP_LIGHT,
                            value = lightSleepMinutes.toString(),
                            unit = "minutes",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "fromStages" to "true"
                            )
                        )
                    )
                }
                
                // REM 수면 기록
                if (remSleepMinutes > 0) {
                    sleepRecords.add(
                        HealthRecord(
                            type = HealthDataType.SLEEP_REM,
                            value = remSleepMinutes.toString(),
                            unit = "minutes",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "fromStages" to "true"
                            )
                        )
                    )
                }
            }
            
            sleepRecords
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 세션이 걸치는 모든 날짜를 반환 (한국 시간대 기준)
    private fun getSessionDates(session: SleepSessionRecord): List<String> {
        val startDate = session.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
        val endDate = session.endTime.atZone(ZoneId.systemDefault()).toLocalDate()
        
        val dates = mutableListOf<String>()
        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            dates.add(currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
            currentDate = currentDate.plusDays(1)
        }
        return dates
    }
    
    // 특정 날짜에 해당하는 세션 시간 부분을 반환
    private fun getSessionTimeForDate(session: SleepSessionRecord, date: String): Pair<Instant, Instant> {
        val targetDate = LocalDate.parse(date)
        val dayStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val dayEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val sessionStart = maxOf(session.startTime, dayStart)
        val sessionEnd = minOf(session.endTime, dayEnd)
        
        return Pair(sessionStart, sessionEnd)
    }
    
    // 특정 날짜에 해당하는 단계 시간 부분을 반환
    private fun getStageTimeForDate(stage: SleepSessionRecord.Stage, date: String): Pair<Instant, Instant>? {
        val targetDate = LocalDate.parse(date)
        val dayStart = targetDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val dayEnd = targetDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        // 단계가 해당 날짜와 겹치는지 확인
        if (stage.endTime <= dayStart || stage.startTime >= dayEnd) {
            return null
        }
        
        val stageStart = maxOf(stage.startTime, dayStart)
        val stageEnd = minOf(stage.endTime, dayEnd)
        
        return Pair(stageStart, stageEnd)
    }

    private fun getDateString(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private suspend fun readStepsData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    type = HealthDataType.STEPS,
                    value = record.count.toString(),
                    unit = "steps",
                    recordTime = record.startTime.toString(),
                    metadata = mapOf(
                        "endTime" to record.endTime.toString()
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readWeightData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    type = HealthDataType.WEIGHT,
                    value = record.weight.inKilograms.toString(),
                    unit = "kg",
                    recordTime = record.time.toString()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readSleepData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                val durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime)
                HealthRecord(
                    type = HealthDataType.SLEEP_TOTAL,
                    value = durationMinutes.toString(),
                    unit = "minutes",
                    recordTime = record.startTime.toString(),
                    metadata = mapOf(
                        "endTime" to record.endTime.toString()
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readHeartRateData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.flatMap { record ->
                record.samples.map { sample ->
                    HealthRecord(
                        type = HealthDataType.HEART_RATE,
                        value = sample.beatsPerMinute.toString(),
                        unit = "bpm",
                        recordTime = sample.time.toString()
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailyHeartRate(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            Log.d("HealthConnectRepository", "Reading heart rate records for aggregation...")
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            Log.d("HealthConnectRepository", "Heart rate query result: ${response.records.size} raw records")
            
            // 일자별 심박수 집계 (평균, 최고, 최저)
            val dailyHeartRate = response.records
                .flatMap { record -> record.samples }
                .groupBy { getDateString(it.time) }
                .map { (date, samples) ->
                    val values = samples.map { it.beatsPerMinute }
                    val average = values.average()
                    val max = values.maxOrNull() ?: 0
                    val min = values.minOrNull() ?: 0
                    
                    Log.d("HealthConnectRepository", "Heart rate for $date: avg=${average.toInt()}, max=$max, min=$min from ${samples.size} samples")
                    
                    listOf(
                        HealthRecord(
                            type = HealthDataType.HEART_RATE_AVERAGE,
                            value = average.toInt().toString(),
                            unit = "bpm",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "sampleCount" to samples.size.toString()
                            )
                        ),
                        HealthRecord(
                            type = HealthDataType.HEART_RATE_MAX,
                            value = max.toString(),
                            unit = "bpm",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "sampleCount" to samples.size.toString()
                            )
                        ),
                        HealthRecord(
                            type = HealthDataType.HEART_RATE_MIN,
                            value = min.toString(),
                            unit = "bpm",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "sampleCount" to samples.size.toString()
                            )
                        )
                    )
                }
                .flatten()
            
            Log.d("HealthConnectRepository", "Aggregated heart rate: ${dailyHeartRate.size} records")
            dailyHeartRate
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error reading heart rate data for aggregation", e)
            emptyList()
        }
    }

    private suspend fun getBloodGlucoseFromSamsungHealth(targetDate: LocalDate): List<HealthRecord> {
        return try {
            Log.d("HealthConnectRepository", "Getting blood glucose from Samsung Health for date: $targetDate")
            
            // 1. Samsung Health 연결
            val isConnected = samsungHealthRepository.connectToSamsungHealth()
            if (!isConnected) {
                Log.w("HealthConnectRepository", "Failed to connect to Samsung Health")
                return emptyList()
            }
            
            // 2. 권한 요청
            val hasPermission = samsungHealthRepository.requestPermissions()
            if (!hasPermission) {
                Log.w("HealthConnectRepository", "Failed to get Samsung Health permissions")
                return emptyList()
            }
            
            // 3. 해당 날짜의 혈당 데이터 가져오기
            val bloodGlucoseData = samsungHealthRepository.getBloodGlucoseDataForDate(targetDate)
            
            Log.d("HealthConnectRepository", "Retrieved ${bloodGlucoseData.size} blood glucose records from Samsung Health for date $targetDate")
            bloodGlucoseData
        } catch (e: Exception) {
            Log.e("HealthConnectRepository", "Error getting blood glucose from Samsung Health", e)
            emptyList()
        }
    }

    private suspend fun aggregateDailyBodyFat(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 체지방 데이터 집계 (최저값)
            val dailyBodyFat = response.records
                .groupBy { getDateString(it.time) }
                .mapNotNull { (date, records) ->
                    val minBodyFat = records.minOfOrNull { it.percentage }
                    
                    Log.d("HealthConnectRepository", "Body fat for $date: min=$minBodyFat from ${records.size} records")
                    
                    if (minBodyFat != null) {
                        HealthRecord(
                            type = HealthDataType.BODY_FAT,
                            value = String.format("%.1f", minBodyFat),
                            unit = "%",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "recordCount" to records.size.toString()
                            )
                        )
                    } else null
                }
            
            dailyBodyFat
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailyMuscleMass(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = LeanBodyMassRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 근육량 데이터 집계 (최저값)
            val dailyMuscleMass = response.records
                .groupBy { getDateString(it.time) }
                .mapNotNull { (date, records) ->
                    val minMuscleMass = records.minOfOrNull { it.mass.inKilograms }
                    
                    Log.d("HealthConnectRepository", "Muscle mass for $date: min=$minMuscleMass from ${records.size} records")
                    
                    if (minMuscleMass != null) {
                        HealthRecord(
                            type = HealthDataType.MUSCLE_MASS,
                            value = String.format("%.1f", minMuscleMass),
                            unit = "kg",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "recordCount" to records.size.toString()
                            )
                        )
                    } else null
                }
            
            dailyMuscleMass
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailyBodyWater(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HydrationRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 체수분량 데이터 집계 (최저값)
            val dailyBodyWater = response.records
                .groupBy { getDateString(it.startTime) }
                .mapNotNull { (date, records) ->
                    val minBodyWater = records.minOfOrNull { it.volume.inLiters }
                    
                    Log.d("HealthConnectRepository", "Body water for $date: min=$minBodyWater from ${records.size} records")
                    
                    if (minBodyWater != null) {
                        HealthRecord(
                            type = HealthDataType.BODY_WATER,
                            value = String.format("%.1f", minBodyWater),
                            unit = "L",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "recordCount" to records.size.toString()
                            )
                        )
                    } else null
                }
            
            dailyBodyWater
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun aggregateDailyBoneMass(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = BoneMassRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            
            // 일자별 골량 데이터 집계 (최저값)
            val dailyBoneMass = response.records
                .groupBy { getDateString(it.time) }
                .mapNotNull { (date, records) ->
                    val minBoneMass = records.minOfOrNull { it.mass.inKilograms }
                    
                    Log.d("HealthConnectRepository", "Bone mass for $date: min=$minBoneMass from ${records.size} records")
                    
                    if (minBoneMass != null) {
                        HealthRecord(
                            type = HealthDataType.BONE_MASS,
                            value = String.format("%.1f", minBoneMass),
                            unit = "kg",
                            recordTime = "${date}T00:00:00Z",
                            metadata = mapOf(
                                "date" to date,
                                "recordCount" to records.size.toString()
                            )
                        )
                    } else null
                }
            
            dailyBoneMass
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readExerciseData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                val durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime)
                HealthRecord(
                    type = HealthDataType.EXERCISE,
                    value = durationMinutes.toString(),
                    unit = "minutes",
                    recordTime = record.startTime.toString(),
                    metadata = mapOf(
                        "endTime" to record.endTime.toString(),
                        "exerciseType" to record.exerciseType.toString()
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readBloodPressureData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = BloodPressureRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    type = HealthDataType.BLOOD_PRESSURE,
                    value = "${record.systolic.inMillimetersOfMercury.roundToInt()}/${record.diastolic.inMillimetersOfMercury.roundToInt()}",
                    unit = "mmHg",
                    recordTime = record.time.toString()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun readCaloriesData(timeRange: TimeRangeFilter): List<HealthRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = timeRange
            )
            val response = healthConnectClient.readRecords(request)
            response.records.map { record ->
                HealthRecord(
                    type = HealthDataType.CALORIES,
                    value = (record.energy.inCalories / 1000).toInt().toString(), // 정수로 변환
                    unit = "kcal",
                    recordTime = record.startTime.toString(),
                    metadata = mapOf(
                        "endTime" to record.endTime.toString()
                    )
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
} 