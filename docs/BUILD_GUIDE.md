# CarrotPlay - 빌드 가이드

## 1. 개요

CarrotPlay는 **Android 시스템 앱**으로 빌드됩니다.
- `android.uid.system` sharedUserId 사용
- **AOSP 플랫폼 키로 서명** 필수
- 시스템 권한으로 실행

---

## 2. 요구사항

### 2.1 필수 도구
| 도구 | 버전 | 용도 |
|------|------|------|
| Android Studio | Hedgehog+ | IDE |
| Kotlin | 1.9+ | 언어 |
| Android SDK | API 34 | 빌드 |
| Java | JDK 17 | 빌드 |
| AOSP 플랫폼 키 | - | 서명 |

### 2.2 개발 환경 경로 (예시)
```
Android SDK: C:\Users\{user}\AppData\Local\Android\sdk
Build Tools: {SDK}\build-tools\35.0.0
AOSP Keys:   D:\nMirror\tools\aosp_keys\
```

### 2.3 키 파일
```
D:\nMirror\tools\aosp_keys\
├── platform.p12          # PKCS#12 키스토어
├── platform.pk8          # 개인 키 (대체)
└── platform.x509.pem     # 인증서 (대체)
```

| 파일 | Alias | Password |
|------|-------|----------|
| `platform.p12` | `platform` | `android` |

---

## 3. 패키지 정보

| 항목 | 값 | 설명 |
|------|-----|------|
| Package Name | `android.test.settings` | ⚠️ 변경 금지 |
| sharedUserId | `android.uid.system` | 시스템 권한 |
| compileSdk | 34 | 빌드 SDK |
| targetSdk | 34 | 타겟 SDK |
| minSdk | 29 | 최소 SDK (Android 10) |
| versionCode | 1 | 버전 코드 |
| versionName | 1.0.0 | 버전 이름 |

> ⚠️ **중요**: 패키지명 `android.test.settings`는 시스템에서 허용된 이름입니다.
> 변경하면 `INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID` 오류 발생

---

## 4. 빌드 방법

### 4.1 Android Studio에서 빌드
1. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`
2. 출력: `app/build/outputs/apk/debug/app-debug.apk`

### 4.2 명령줄 빌드
```powershell
cd D:\nMirror\CarrotPlay

# Debug 빌드
./gradlew assembleDebug

# Release 빌드
./gradlew assembleRelease
```

---

## 5. 서명 방법

### 5.1 apksigner 사용 (권장)
```powershell
$APKSIGNER = "C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0\apksigner.bat"
$KEYSTORE = "D:\nMirror\tools\aosp_keys\platform.p12"
$INPUT = "app\build\outputs\apk\debug\app-debug.apk"
$OUTPUT = "app\build\outputs\apk\debug\carrotplay-signed.apk"

& $APKSIGNER sign `
    --ks $KEYSTORE `
    --ks-pass pass:android `
    --ks-key-alias platform `
    --out $OUTPUT `
    $INPUT
```

### 5.2 signapk.jar 사용 (대체)
```powershell
$SIGNAPK = "D:\nMirror\tools\signapk.jar"
$CERT = "D:\nMirror\tools\aosp_keys\platform.x509.pem"
$KEY = "D:\nMirror\tools\aosp_keys\platform.pk8"

java -jar $SIGNAPK $CERT $KEY app-debug.apk carrotplay-signed.apk
```

### 5.3 서명 확인
```powershell
# 서명 정보 확인
& $APKSIGNER verify --print-certs carrotplay-signed.apk

# 예상 출력 (AOSP 플랫폼 키)
# Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, ...
```

---

## 6. 설치 방법

### 6.1 ADB 설치
```powershell
# 기존 앱 제거 (서명 충돌 시)
adb uninstall android.test.settings

# 설치
adb install -r carrotplay-signed.apk

# 실행
adb shell am start -n android.test.settings/.MainActivity
```

### 6.2 시스템 앱으로 설치 (Root 필요)
```powershell
# /system/priv-app에 설치
adb root
adb remount
adb push carrotplay-signed.apk /system/priv-app/CarrotPlay/CarrotPlay.apk
adb shell chmod 644 /system/priv-app/CarrotPlay/CarrotPlay.apk
adb reboot
```

---

## 7. 원샷 스크립트

### 7.1 빌드 + 서명 + 설치
```powershell
# CarrotPlay 빌드/서명/설치 원샷

$ErrorActionPreference = "Stop"

# 경로 설정
$PROJECT_DIR = "D:\nMirror\CarrotPlay"
$APKSIGNER = "C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0\apksigner.bat"
$KEYSTORE = "D:\nMirror\tools\aosp_keys\platform.p12"

Set-Location $PROJECT_DIR

Write-Host "🔨 Building..." -ForegroundColor Cyan
./gradlew assembleDebug

$INPUT_APK = "app\build\outputs\apk\debug\app-debug.apk"
$OUTPUT_APK = "app\build\outputs\apk\debug\carrotplay-signed.apk"

Write-Host "🔐 Signing..." -ForegroundColor Cyan
& $APKSIGNER sign `
    --ks $KEYSTORE `
    --ks-pass pass:android `
    --ks-key-alias platform `
    --out $OUTPUT_APK `
    $INPUT_APK

Write-Host "📲 Installing..." -ForegroundColor Cyan
adb install -r $OUTPUT_APK

Write-Host "🚀 Launching..." -ForegroundColor Cyan
adb shell am start -n android.test.settings/.MainActivity

Write-Host "✅ Done!" -ForegroundColor Green
```

---

## 8. Hidden API 설정

### 8.1 Stub JAR 방식 (현재 사용)
```
app/libs/hidden-api-stub.jar
```

`build.gradle.kts`에서 compileOnly로 참조:
```kotlin
dependencies {
    compileOnly(files("libs/hidden-api-stub.jar"))
}
```

### 8.2 포함된 Hidden API 클래스

| 클래스 | 메서드 | 용도 |
|--------|--------|------|
| `android.hardware.input.InputManager` | `getInstance()`, `injectInputEvent()` | 입력 주입 |
| `android.os.ServiceManager` | `getService()` | 시스템 서비스 접근 |
| `android.view.WindowManagerGlobal` | `getWindowManagerService()` | IWindowManager 획득 |
| `android.view.IWindowManager` | `syncInputTransactions()` | 입력 동기화 |

---

## 9. 트러블슈팅

### 9.1 설치 오류

| 오류 | 원인 | 해결 |
|------|------|------|
| `INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID` | 패키지명이 `android.test.settings`가 아님 | 패키지명 확인 |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 기존 앱과 서명 다름 | `adb uninstall` 후 재설치 |
| `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` | 서명 키가 플랫폼 키가 아님 | AOSP 키로 서명 |

### 9.2 런타임 오류

| 오류 | 원인 | 해결 |
|------|------|------|
| `SecurityException: INJECT_EVENTS` | 권한 없음 | 플랫폼 키 서명 확인 |
| `NoSuchMethodError: InputManager` | Hidden API stub 누락 | libs/hidden-api-stub.jar 확인 |

### 9.3 빌드 오류

| 오류 | 원인 | 해결 |
|------|------|------|
| `Unresolved reference: InputManager` | Hidden API stub 없음 | `compileOnly(files("libs/hidden-api-stub.jar"))` 확인 |
| `Kotlin version mismatch` | Kotlin 버전 불일치 | kotlinCompilerExtensionVersion 확인 |

---

## 10. 참고 사항

### 10.1 AOSP 플랫폼 키 획득
- 차량 제조사에서 제공 (정식 방법)
- AOSP 빌드 시 생성
- 테스트용: AOSP 기본 키 (보안 취약)

### 10.2 Compose 버전 정보
```kotlin
// build.gradle.kts
composeOptions {
    kotlinCompilerExtensionVersion = "1.5.6"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
}
```

### 10.3 관련 문서
- [PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md) - 프로젝트 개요
- [ARCHITECTURE.md](./ARCHITECTURE.md) - 아키텍처 설계
- [UI_UX_GUIDELINES.md](./UI_UX_GUIDELINES.md) - UI/UX 가이드
