# 헬스 커넥트 웹훅 앱

## 📱 개요

이 앱은 Android Health Connect에서 건강 데이터를 주기적으로 수집하여 지정된 웹훅 URL로 전송하는 애플리케이션입니다.

## ✨ 주요 기능

- **헬스 커넥트 통합**: Android Health Connect API를 통한 건강 데이터 접근
- **자동 동기화**: 사용자가 설정한 간격으로 자동 데이터 전송
- **즉시 동기화**: 수동으로 즉시 데이터 동기화 실행
- **다양한 건강 데이터 지원**:
  - 걸음 수 (Steps)
  - 심박수 (Heart Rate)
  - 체중 (Weight)
  - 수면 데이터 (Sleep)
  - 운동 세션 (Exercise)
  - 혈압 (Blood Pressure)
  - 칼로리 소모량 (Calories)

## 🏗️ 아키텍처

- **MVVM 패턴**: ViewModel을 통한 UI 상태 관리
- **Hilt**: 의존성 주입
- **Jetpack Compose**: 현대적인 UI 프레임워크
- **WorkManager**: 백그라운드 작업 스케줄링
- **DataStore**: 사용자 설정 저장
- **Retrofit**: 웹훅 HTTP 통신

## 📋 시스템 요구사항

- **Android SDK**: API 28 (Android 9.0) 이상
- **헬스 커넥트**: 
  - Android 14 이상: 시스템 내장
  - Android 13 이하: Google Play 스토어에서 별도 설치 필요

## 🚀 설치 및 설정

1. **프로젝트 빌드**
   ```bash
   ./gradlew assembleDebug
   ```

2. **헬스 커넥트 설치** (Android 13 이하)
   - Google Play 스토어에서 "Health Connect" 검색 후 설치

3. **앱 설정**
   - 헬스 커넥트 권한 승인
   - 웹훅 URL 설정
   - 사용자 ID 설정
   - 동기화 간격 설정 (최소 15분)

## 📊 웹훅 데이터 형식

```json
{
  "deviceId": "android_device_id",
  "userId": "user123",
  "appVersion": "1.0.0",
  "healthData": {
    "timestamp": "2024-01-15T10:30:00",
    "steps": {
      "count": 8524,
      "startTime": "2024-01-15T00:00:00Z",
      "endTime": "2024-01-15T10:30:00Z"
    },
    "heartRate": {
      "beatsPerMinute": 72,
      "timestamp": "2024-01-15T10:30:00Z"
    },
    "weight": {
      "kilograms": 70.5,
      "timestamp": "2024-01-15T08:00:00Z"
    },
    "sleep": {
      "durationMinutes": 480,
      "startTime": "2024-01-14T23:00:00Z",
      "endTime": "2024-01-15T07:00:00Z",
      "stages": [
        {
          "stage": "DEEP",
          "startTime": "2024-01-14T23:30:00Z",
          "endTime": "2024-01-15T01:30:00Z"
        }
      ]
    },
    "exercise": {
      "type": "RUNNING",
      "durationMinutes": 30,
      "startTime": "2024-01-15T07:00:00Z",
      "endTime": "2024-01-15T07:30:00Z",
      "caloriesBurned": 200.5
    },
    "bloodPressure": {
      "systolic": 120.0,
      "diastolic": 80.0,
      "timestamp": "2024-01-15T09:00:00Z"
    },
    "calories": {
      "activeCalories": 350.5,
      "totalCalories": 1850.0,
      "startTime": "2024-01-15T00:00:00Z",
      "endTime": "2024-01-15T10:30:00Z"
    }
  }
}
```

## 🔒 개인정보 보호

- 모든 건강 데이터는 사용자 동의 하에만 수집됩니다
- 데이터는 기기 내부에 암호화되어 저장됩니다
- 사용자가 언제든지 권한을 철회할 수 있습니다
- 웹훅 전송은 HTTPS를 권장합니다

## 🛠️ 개발 환경

- **Kotlin**: 1.9.0+
- **Android Gradle Plugin**: 8.2.0+
- **Compose BOM**: 2024.02.00+
- **Health Connect**: 1.1.0-alpha07

## 📱 사용법

1. **초기 설정**
   - 앱 실행 후 헬스 커넥트 권한 요청 승인
   - 웹훅 URL과 사용자 ID 입력

2. **동기화 설정**
   - 원하는 동기화 간격 설정 (최소 15분)
   - 자동 동기화 토글 활성화

3. **즉시 동기화**
   - "지금 동기화" 버튼으로 수동 실행 가능

## ⚠️ 주의사항

- 배터리 최적화 설정에서 앱을 제외하여 백그라운드 동기화 보장
- 네트워크 연결이 필요한 작업이므로 Wi-Fi 환경 권장
- 헬스 커넥트에 데이터가 없으면 빈 객체가 전송됩니다

## 🔧 문제 해결

**헬스 커넥트가 작동하지 않는 경우:**
- Android 13 이하: Google Play 스토어에서 Health Connect 앱 설치
- 권한이 제대로 승인되었는지 확인
- 다른 건강 앱들이 데이터를 기록하고 있는지 확인

**동기화가 되지 않는 경우:**
- 네트워크 연결 상태 확인
- 웹훅 URL이 올바른지 확인
- 배터리 최적화 설정 확인

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 