# CarrotPlay 빌드 가이드

## 개요

CarrotPlay는 Android 시스템 앱으로 빌드됩니다. `android.uid.system` sharedUserId를 사용하여 시스템 권한으로 실행되며, AOSP 플랫폼 키로 서명해야 합니다.

## 요구사항

### 필수 도구
- Flutter SDK (3.x 이상)
- Android SDK (API 34+)
- Java 17 (JDK)
- AOSP 플랫폼 키 (`platform.p12`)

### 개발 환경 경로
```
Flutter SDK: C:\flutter
Android SDK: C:\Users\jomin\AppData\Local\Android\sdk
Build Tools: C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0
AOSP Keys: D:\nMirror\tools\aosp_keys\platform.p12
```

### 키 파일 위치
```
D:\nMirror\tools\aosp_keys\platform.p12
- Alias: platform
- Password: android
```

## 패키지 정보

| 항목 | 값 |
|------|-----|
| Package Name | `android.test.settings` |
| sharedUserId | `android.uid.system` |
| targetSdk | 35 |
| compileSdk | 36 |

> ⚠️ **중요**: 패키지명 `android.test.settings`는 시스템에서 허용된 이름입니다. 변경하면 설치 실패합니다.

## 빌드 방법

### 1. Flutter 빌드

```powershell
cd D:\nMirror\carcar_launcher
C:\flutter\bin\flutter clean
C:\flutter\bin\flutter build apk --debug
```

### 2. 플랫폼 키로 서명

```powershell
$APKSIGNER = "C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0\apksigner.bat"
$KEYSTORE = "D:\nMirror\tools\aosp_keys\platform.p12"

& $APKSIGNER sign `
    --ks $KEYSTORE `
    --ks-pass pass:android `
    --ks-key-alias platform `
    --out "build\app\outputs\flutter-apk\carcar-signed.apk" `
    "build\app\outputs\flutter-apk\app-debug.apk"
```

### 3. 설치

```powershell
# 기존 앱 제거 (필요시)
adb uninstall android.test.settings

# 설치
adb install "build\app\outputs\flutter-apk\carcar-signed.apk"
```

## 원샷 빌드 스크립트

```powershell
cd D:\nMirror\carcar_launcher

# 빌드
C:\flutter\bin\flutter build apk --debug

# 서명
$APKSIGNER = "C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0\apksigner.bat"
& $APKSIGNER sign --ks "D:\nMirror\tools\aosp_keys\platform.p12" --ks-pass pass:android --ks-key-alias platform --out "build\app\outputs\flutter-apk\carcar-signed.apk" "build\app\outputs\flutter-apk\app-debug.apk"

# 설치
adb install -r "build\app\outputs\flutter-apk\carcar-signed.apk"

# 실행
adb shell am start -n android.test.settings/.MainActivity
```

## 프로젝트 구조

```
carcar_launcher/
├── android/app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml      # 시스템 권한 선언
│   │   ├── aidl/                    # Hidden API AIDL
│   │   │   └── android/view/
│   │   │       ├── IWindowManager.aidl
│   │   │       └── IWindowSession.aidl
│   │   ├── java/                    # Hidden API Stub
│   │   │   └── android/
│   │   │       ├── os/ServiceManager.java
│   │   │       ├── view/WindowManagerGlobal.java
│   │   │       └── hardware/input/InputManager.java
│   │   └── kotlin/android/test/settings/
│   │       ├── MainActivity.kt       # Flutter Activity
│   │       ├── TouchInjector.kt      # 터치 주입 (Hidden API)
│   │       ├── TaskManager.kt        # 태스크 관리
│   │       ├── RootUtils.kt          # Root 유틸리티
│   │       └── VirtualDisplayManager.kt
│   └── build.gradle.kts
├── lib/                              # Flutter Dart 코드
└── docs/                             # 문서
```

## Hidden API 사용

이 프로젝트는 다음 Hidden API를 사용합니다:

| API | 용도 |
|-----|------|
| `InputManager.getInstance()` | 터치 이벤트 주입 |
| `InputManager.injectInputEvent()` | MotionEvent 주입 |
| `WindowManagerGlobal.getWindowManagerService()` | IWindowManager 획득 |
| `IWindowManager.syncInputTransactions()` | 입력 동기화 |

**AIDL + Java Stub 방식**으로 컴파일 타임에 접근합니다. 런타임에는 실제 시스템 클래스가 사용됩니다.

## 트러블슈팅

### INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID
- 패키지명이 `android.test.settings`가 아니면 발생
- 서명 키가 플랫폼 키가 아니면 발생

### INSTALL_FAILED_UPDATE_INCOMPATIBLE
- 기존 앱과 서명이 다름
- `adb uninstall android.test.settings` 후 재설치

### Hidden API 접근 오류
- AIDL 파일 누락 확인
- Java stub 파일 확인
- `build.gradle.kts`에서 AIDL 설정 확인

## 참고

- 원본 앱: `android.test.settings.apk` (CarCarLauncher)
- 터치 주입 원본 코드: `z7/q.java`, `z7/g.java`
