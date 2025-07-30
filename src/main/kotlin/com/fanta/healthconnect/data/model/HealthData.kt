package com.fanta.healthconnect.data.model

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class HealthDataPayload(
    val timestamp: String,
    val deviceId: String,
    val userId: String,
    val dataType: String,
    val data: List<HealthRecord>
)

@Serializable
data class HealthRecord(
    val type: HealthDataType,
    val value: String,
    val unit: String,
    val recordTime: String,
    val metadata: Map<String, String> = emptyMap()
)

enum class HealthDataType {
    STEPS,
    HEART_RATE,
    HEART_RATE_AVERAGE,
    HEART_RATE_MAX,
    HEART_RATE_MIN,
    WEIGHT,
    SLEEP_TOTAL,
    SLEEP_DEEP,
    SLEEP_LIGHT,
    SLEEP_REM,
    EXERCISE,
    BLOOD_PRESSURE,
    CALORIES,
    BLOOD_GLUCOSE,
    BLOOD_GLUCOSE_MAX,
    BLOOD_GLUCOSE_MIN,
    BODY_FAT,
    MUSCLE_MASS,
    BODY_WATER,
    BONE_MASS,
    VISCERAL_FAT_INDEX
}

data class StepsData(
    val count: Long,
    val startTime: Instant,
    val endTime: Instant
)

data class HeartRateData(
    val beatsPerMinute: Long,
    val time: Instant
)

data class WeightData(
    val weightKg: Double,
    val time: Instant
)

data class SleepData(
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Long
)

data class ExerciseData(
    val type: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Long,
    val caloriesBurned: Double?
)

data class BloodPressureData(
    val systolic: Double,
    val diastolic: Double,
    val time: Instant
)

data class CaloriesData(
    val calories: Double,
    val startTime: Instant,
    val endTime: Instant
) 