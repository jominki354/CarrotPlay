# 프로젝트 아키텍처

## 개요

CarrotPlay는 Android 차량용 런처로, VirtualDisplay에서 다른 앱을 실행하고 PIP(Picture-in-Picture) 형태로 표시합니다.

## 핵심 컴포넌트

### 1. TouchInjector (터치 주입)

**파일**: `android/app/src/main/kotlin/android/test/settings/TouchInjector.kt`

원본 CarCarLauncher의 `z7/q.java` 방식을 구현합니다.

```kotlin
// 핵심 로직
InputManager.getInstance().injectInputEvent(motionEvent, INJECT_INPUT_EVENT_MODE_ASYNC)
WindowManagerGlobal.getWindowManagerService().syncInputTransactions(true)
```

**기능**:
- `injectMotionEvent()`: Flutter에서 받은 터치를 VirtualDisplay로 주입
- `injectTap()`: 단순 탭 이벤트
- `injectSwipe()`: 스와이프 제스처
- `injectKeyEvent()`: Back, Home 키 이벤트

### 2. TaskManager (태스크 관리)

**파일**: `android/app/src/main/kotlin/android/test/settings/TaskManager.kt`

VirtualDisplay에서 실행 중인 앱의 태스크를 관리합니다.

**기능**:
- 앱 실행 (특정 displayId에서)
- 태스크 이동/종료
- 화면 캡처

### 3. MainActivity (Flutter 브릿지)

**파일**: `android/app/src/main/kotlin/android/test/settings/MainActivity.kt`

Flutter와 Native 코드를 연결하는 MethodChannel을 제공합니다.

**채널**: `com.carrotplay/system`

| 메서드 | 설명 |
|--------|------|
| `createVirtualDisplay` | VirtualDisplay 생성 |
| `injectTouch` | 터치 이벤트 주입 |
| `launchApp` | 앱 실행 |
| `getInstalledApps` | 설치된 앱 목록 |

## Flutter 측 구조

```
lib/
├── main.dart                 # 앱 진입점
├── screens/
│   ├── home_screen.dart      # 메인 런처 화면
│   └── pip_screen.dart       # PIP 뷰
├── services/
│   ├── platform_service.dart # Native 통신
│   └── app_service.dart      # 앱 관리
└── widgets/
    └── app_grid.dart         # 앱 그리드 위젯
```

## 데이터 흐름

```
[Flutter UI] 
    ↓ MethodChannel
[MainActivity.kt]
    ↓
[TouchInjector / TaskManager]
    ↓ Hidden API
[Android System]
    ↓
[VirtualDisplay → Target App]
```

## 권한 모델

| 권한 | 용도 | 획득 방법 |
|------|------|----------|
| INJECT_EVENTS | 터치 주입 | sharedUserId + 플랫폼 서명 |
| MANAGE_ACTIVITY_TASKS | 태스크 관리 | sharedUserId + 플랫폼 서명 |
| ADD_TRUSTED_DISPLAY | VirtualDisplay | sharedUserId + 플랫폼 서명 |
| SYSTEM_ALERT_WINDOW | 오버레이 | 런타임 요청 |

## 원본 앱 참조

| 원본 클래스 | 구현 파일 | 설명 |
|------------|----------|------|
| `z7/q.java` | `TouchInjector.kt` | InputManager 기반 터치 주입 |
| `z7/g.java` | `TouchInjector.kt` | MotionEvent 변환 |
| `p6/a.java` | `TaskManager.kt` | 태스크/액티비티 관리 |
