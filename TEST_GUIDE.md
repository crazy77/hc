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
adb shell am start -n com.example.healthconnectwebhook/.presentation.MainActivity
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
adb shell dumpsys package com.example.healthconnectwebhook | grep permission

# 권한 수동 부여 (테스트용)
adb shell pm grant com.example.healthconnectwebhook android.permission.health.READ_STEPS
```

### 백그라운드 작업이 실행되지 않는 경우
```bash
# 배터리 최적화 해제
adb shell dumpsys deviceidle whitelist +com.example.healthconnectwebhook

# WorkManager 작업 상태 확인
adb shell dumpsys jobscheduler | grep HealthDataSyncWorker
```

## 📈 성능 테스트

### 배터리 사용량 모니터링
```bash
# 배터리 통계 확인
adb shell dumpsys batterystats | grep com.example.healthconnectwebhook
```

### 네트워크 사용량 확인
```bash
# 네트워크 통계
adb shell dumpsys netstats | grep com.example.healthconnectwebhook
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