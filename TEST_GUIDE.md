# 🧪 헬스 커넥트 웹훅 앱 테스트 가이드

## 📋 테스트 준비사항

### 1. 개발 환경 설정
```bash
# 1. Android Studio 설치 (최신 버전)
# 2. Android SDK 설치 (API 28 이상)
# 3. 실제 Android 기기 또는 에뮬레이터 준비
```

### 2. 프로젝트 빌드
```bash
# 프로젝트 루트 디렉터리에서 실행
./gradlew clean
./gradlew assembleDebug
```

### 3. 헬스 커넥트 설치 (Android 13 이하)
- **방법 1**: Google Play 스토어에서 "Health Connect" 검색 후 설치
- **방법 2**: APK 직접 다운로드 (개발자 사이트)

## 🌐 웹훅 테스트 서버 설정

### Option 1: webhook.site 사용 (권장)
1. [webhook.site](https://webhook.site) 접속
2. 고유 URL 자동 생성됨 (예: `https://webhook.site/12345abc-...")
3. 실시간으로 요청 확인 가능

### Option 2: ngrok 사용
```bash
# 로컬 서버 실행
python -m http.server 8080

# 새 터미널에서 ngrok 실행
ngrok http 8080
```

### Option 3: 간단한 Node.js 서버
```javascript
// webhook-server.js
const express = require('express');
const app = express();

app.use(express.json());

app.post('/webhook', (req, res) => {
    console.log('받은 건강 데이터:', JSON.stringify(req.body, null, 2));
    res.status(200).send('OK');
});

app.listen(3000, () => {
    console.log('웹훅 서버가 포트 3000에서 실행 중...');
});
```

## 📱 앱 테스트 단계

### 1단계: 앱 설치 및 실행
```bash
# APK 설치
adb install app/build/outputs/apk/debug/app-debug.apk

# 앱 실행
adb shell am start -n com.fanta.healthconnect/.presentation.MainActivity
```

### 2단계: 헬스 커넥트 권한 설정
1. 앱 실행 후 "권한 요청" 버튼 클릭
2. 헬스 커넥트 권한 화면에서 다음 권한 승인:
   - ✅ 걸음 수 읽기
   - ✅ 심박수 읽기
   - ✅ 체중 읽기
   - ✅ 수면 데이터 읽기
   - ✅ 운동 데이터 읽기
   - ✅ 혈압 읽기
   - ✅ 칼로리 읽기

### 3단계: 앱 설정 구성
1. **웹훅 URL**: 위에서 준비한 테스트 서버 URL 입력
2. **사용자 ID**: 테스트용 ID 입력 (예: `test_user_123`)
3. **동기화 간격**: 15분으로 설정 (테스트용)

### 4단계: 테스트 데이터 생성

#### Google Fit 사용
```bash
# Google Fit 앱 설치
# 걸음 수, 운동 데이터 등 기록
```

#### Samsung Health 사용
```bash
# Samsung Health 앱 설치
# 다양한 건강 데이터 입력
```

#### 수동 데이터 입력 (헬스 커넥트 도구 사용)
```kotlin
// 테스트용 데이터 삽입 코드 (개발자 도구)
val testStepsRecord = StepsRecord(
    count = 1000,
    startTime = Instant.now().minus(1, ChronoUnit.HOURS),
    endTime = Instant.now(),
    startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(Instant.now()),
    endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(Instant.now())
)
```

## 🔍 테스트 시나리오

### 기본 기능 테스트
```
✅ 헬스 커넥트 연결 확인
✅ 권한 요청 및 승인
✅ 설정 저장 확인
✅ 즉시 동기화 실행
✅ 주기적 동기화 활성화
```

### 데이터 전송 테스트
```
✅ 걸음 수 데이터 전송
✅ 심박수 데이터 전송  
✅ 체중 데이터 전송
✅ 수면 데이터 전송
✅ 운동 데이터 전송
✅ 혈압 데이터 전송
✅ 칼로리 데이터 전송
```

### 에러 처리 테스트
```
✅ 네트워크 연결 없음
✅ 잘못된 웹훅 URL
✅ 서버 응답 오류 (5xx)
✅ 권한 거부 상황
✅ 헬스 커넥트 미설치
```

## 📊 로그 확인

### Android Studio Logcat
```bash
# 앱 로그 필터링
adb logcat | grep "HealthConnect"
```

### 주요 로그 태그
- `HealthConnectRepository`: 데이터 읽기 관련
- `WebhookRepository`: 웹훅 전송 관련
- `HealthDataSyncWorker`: 백그라운드 작업 관련

## 🐛 문제 해결

### 헬스 커넥트가 인식되지 않는 경우
```bash
# 헬스 커넥트 앱 상태 확인
adb shell pm list packages | grep health

# 헬스 커넥트 앱 정보
adb shell dumpsys package com.google.android.apps.healthdata
```

### 권한이 작동하지 않는 경우
```bash
# 앱 권한 상태 확인
adb shell dumpsys package com.fanta.healthconnect | grep permission

# 권한 수동 부여 (테스트용)
adb shell pm grant com.fanta.healthconnect android.permission.health.READ_STEPS
```

### 백그라운드 작업이 실행되지 않는 경우
```bash
# 배터리 최적화 해제
adb shell dumpsys deviceidle whitelist +com.fanta.healthconnect

# WorkManager 작업 상태 확인
adb shell dumpsys jobscheduler | grep HealthDataSyncWorker
```

## 📈 성능 테스트

### 배터리 사용량 모니터링
```bash
# 배터리 통계 확인
adb shell dumpsys batterystats | grep com.fanta.healthconnect
```

### 네트워크 사용량 확인
```bash
# 네트워크 통계
adb shell dumpsys netstats | grep com.fanta.healthconnect
```

## ✅ 테스트 체크리스트

### 기본 설치 및 설정
- [ ] 앱이 정상적으로 설치됨
- [ ] 헬스 커넥트가 설치되어 있음
- [ ] 모든 권한이 승인됨
- [ ] 웹훅 URL이 올바르게 설정됨

### 기능 테스트
- [ ] 즉시 동기화가 작동함
- [ ] 주기적 동기화가 설정됨
- [ ] 웹훅 서버에 데이터가 전송됨
- [ ] JSON 형식이 올바름

### 에러 처리
- [ ] 네트워크 오류 시 재시도함
- [ ] 잘못된 설정 시 오류 메시지 표시
- [ ] 권한 없을 시 적절한 안내

### 사용자 경험
- [ ] UI가 직관적임
- [ ] 설정 변경이 즉시 반영됨
- [ ] 오류 메시지가 명확함

## 🚀 프로덕션 배포 전 확인사항

1. **보안 검토**
   - HTTPS 웹훅 URL 사용
   - API 키 또는 인증 토큰 설정
   - 민감한 데이터 암호화

2. **성능 최적화**
   - 동기화 간격 조정 (15분 이상)
   - 배터리 사용량 최적화
   - 네트워크 사용량 최소화

3. **규정 준수**
   - 개인정보 처리 정책 작성
   - GDPR/개인정보보호법 준수
   - 사용자 동의 절차 강화

이 가이드를 통해 체계적으로 앱을 테스트하고 문제를 해결할 수 있습니다! 🎯 

## 📱 테스트 환경 설정

### 1. 필수 요구사항
- Android Studio (최신 버전 권장)
- Android SDK (최소 API 26, 권장 API 34)
- Android 기기 또는 에뮬레이터 (Android 8.0+ / API 26+)
- Health Connect 앱 설치 (Google Play Store에서 다운로드)

### 2. 웹훅 서버 준비

#### 옵션 A: webhook.site 사용 (가장 간단함)
1. https://webhook.site 접속
2. 생성된 고유 URL 복사 (예: `https://webhook.site/YOUR-UNIQUE-ID`)
3. 이 URL을 앱의 웹훅 URL 설정에 입력

#### 옵션 B: ngrok 사용 (로컬 개발)
```bash
# ngrok 설치 후
ngrok http 3000
# 생성된 HTTPS URL 사용
```

#### 옵션 C: 간단한 Node.js 서버
```javascript
const express = require('express');
const app = express();

app.use(express.json());

app.post('/webhook', (req, res) => {
    console.log('받은 건강 데이터:', JSON.stringify(req.body, null, 2));
    res.status(200).json({ success: true });
});

app.listen(3000, () => {
    console.log('웹훅 서버가 포트 3000에서 실행 중');
});
```

## 🚀 앱 실행 및 테스트

### 1. 앱 빌드 및 설치
```bash
# 프로젝트 폴더에서
./gradlew assembleDebug
# 또는 Android Studio에서 Run 버튼 클릭
```

### 2. 초기 설정 테스트

#### Health Connect 권한 설정
1. 앱 시작 후 "Health Connect 권한 요청" 버튼 클릭
2. Health Connect 앱이 열리면 필요한 권한들 허용
   - 걸음수, 심박수, 체중, 수면, 운동, 혈압, 칼로리 권한
3. 앱으로 돌아와서 권한 상태 확인

#### 웹훅 URL 설정
1. "설정" 카드에서 웹훅 URL 입력
2. 동기화 간격 설정 (최소 15분)
3. "설정 저장" 버튼 클릭

### 3. 자동 동기화 최적화 (중요!)

앱에서 "백그라운드 동기화 최적화" 카드를 확인하고 다음을 설정:

#### 배터리 최적화 제외
1. "배터리 최적화 제외 설정" 버튼 클릭
2. 앱을 "최적화하지 않음"으로 설정

#### 자동 시작 관리 (제조사별)
1. "자동 시작 관리 설정" 버튼 클릭
2. 제조사별 설정:
   - **Samsung**: 설정 → 디바이스 케어 → 배터리 → 백그라운드 앱 제한
   - **Huawei**: 설정 → 앱 → 앱 시작 관리
   - **Xiaomi**: 보안 → 자동시작 관리
   - **OnePlus**: 설정 → 배터리 → 배터리 최적화
   - **Oppo/Realme**: 설정 → 배터리 → 앱 절전

#### 추가 설정
- **알림 허용**: 앱의 알림을 허용으로 설정
- **백그라운드 앱 새로고침**: 허용으로 설정
- **데이터 사용량**: 백그라운드 데이터 사용 허용

## 🧪 테스트 시나리오

### 1. 수동 동기화 테스트
1. Health Connect에 테스트 데이터가 있는지 확인
2. 날짜 선택 후 "오늘 데이터 수동 동기화" 버튼 클릭
3. 웹훅 서버에서 데이터 수신 확인

**예상 JSON 형태:**
```json
{
  "userId": "user123",
  "timestamp": "2024-01-20T10:30:00Z",
  "healthData": [
    {
      "type": "STEPS",
      "value": "8542",
      "unit": "steps",
      "recordTime": "2024-01-20T09:00:00Z"
    },
    {
      "type": "HEART_RATE",
      "value": "72",
      "unit": "bpm",
      "recordTime": "2024-01-20T09:15:00Z"
    }
  ]
}
```

### 2. 자동 동기화 테스트

#### 단기 테스트 (15-30분)
1. 자동 동기화 활성화
2. 앱을 백그라운드로 전환
3. 15-30분 후 웹훅 서버 확인
4. "백그라운드 동기화 최적화" 카드에서 상태 확인

#### 장기 테스트 (1-2시간)
1. 자동 동기화 활성화 후 앱 종료
2. 정상적인 일상 활동 (걸음수, 심박수 등 데이터 생성)
3. 1-2시간 후 웹훅 데이터 확인
4. 앱 재실행하여 "마지막 동기화" 시간 확인

### 3. 오류 상황 테스트

#### 네트워크 연결 없음
1. 비행기 모드 활성화
2. 수동 동기화 시도
3. 네트워크 연결 후 자동 재시도 확인

#### 잘못된 웹훅 URL
1. 존재하지 않는 URL 설정
2. 동기화 시도 후 오류 메시지 확인

#### Health Connect 권한 취소
1. Health Connect에서 권한 취소
2. 앱에서 권한 상태 변화 확인

## 🔧 문제 해결

### 자동 동기화가 작동하지 않는 경우

#### 1. 배터리 최적화 확인
- 앱의 "백그라운드 동기화 최적화" 카드 확인
- 배터리 최적화가 활성화되어 있다면 제외 설정

#### 2. WorkManager 상태 확인
- 앱에서 "백그라운드 상태" 확인
- "차단됨" 또는 "취소됨" 상태라면 자동 동기화 재시작

#### 3. 로그 확인
```bash
# Android Studio Logcat에서 다음 태그 필터링
adb logcat -s HealthDataSyncWorker ScheduleHealthSyncUseCase
```

#### 4. 제조사별 특별 설정
- **Samsung**: Smart Switch 또는 Game Launcher의 절전 모드 확인
- **Huawei**: PowerGenie에서 앱 보호 설정
- **Xiaomi**: MIUI 최적화 및 자동시작 관리
- **OnePlus**: 고급 최적화에서 앱 보호

### 일반적인 문제

#### Health Connect 연결 실패
```
해결책:
1. Health Connect 앱 업데이트
2. Health Connect 데이터 초기화
3. 기기 재시작
```

#### 웹훅 전송 실패
```
해결책:
1. 웹훅 URL 형식 확인 (https://로 시작)
2. 네트워크 연결 상태 확인
3. 웹훅 서버 응답 확인 (200 상태 코드)
```

#### 데이터가 없는 경우
```
해결책:
1. Health Connect에서 다른 앱의 데이터 확인
2. Google Fit, Samsung Health 등과 연동 확인
3. 수동으로 테스트 데이터 입력
```

## 📊 성능 모니터링

### 1. 배터리 사용량 확인
```bash
# 배터리 사용량 통계
adb shell dumpsys batterystats | grep healthconnectwebhook
```

### 2. WorkManager 작업 상태
```bash
# WorkManager 상태 확인
adb shell dumpsys jobscheduler | grep healthconnectwebhook
```

### 3. 네트워크 사용량
```bash
# 네트워크 통계 확인
adb shell dumpsys netstats detail | grep healthconnectwebhook
```

## 📦 프로덕션 배포 전 체크리스트

- [ ] 모든 테스트 시나리오 통과
- [ ] 자동 동기화 24시간 안정성 테스트
- [ ] 다양한 Android 버전에서 테스트 (API 26-34)
- [ ] 주요 제조사 기기에서 테스트 (Samsung, LG, Huawei, Xiaomi 등)
- [ ] 배터리 사용량 최적화 확인
- [ ] 네트워크 오류 상황 대응 테스트
- [ ] Health Connect 권한 시나리오 테스트
- [ ] 웹훅 서버 부하 테스트
- [ ] 개인정보 보호 정책 준수 확인
- [ ] Play Store 정책 준수 확인

---

**참고**: 이 가이드는 개발 및 테스트 목적으로 작성되었습니다. 실제 사용자에게 배포하기 전에 충분한 테스트와 보안 검토를 수행하시기 바랍니다. 