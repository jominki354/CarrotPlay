# 프로젝트 아키텍처

## 개요

CarrotPlay는 Android 차량용 런처로, VirtualDisplay에서 다른 앱을 실행하고 PIP(Picture-in-Picture) 형태로 표시합니다.

## 주요 기능

- **분할 PIP 모드**: 2개 VirtualDisplay를 좌우 분할로 표시
- **비율 조절**: 30:70 ~ 70:30 (5% 단위) 비율 슬라이더
- **프리셋 시스템**: 3개 프리셋, 앱/비율/스케일 저장
- **앱 서랍**: 그리드 레이아웃, 페이징, 슬라이드 닫기
- **터치 주입**: InputManager Hidden API 기반 실시간 터치

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
├── main.dart                 # 앱 진입점, AppCache 초기화
├── home_screen.dart          # 메인 런처 화면 (Dock + Split PIP)
├── pip_view.dart             # VirtualDisplay PIP 뷰 (터치 주입, 스케일)
├── preset_service.dart       # 프리셋 관리 (비율, 앱 설정)
├── preset_editor.dart        # 프리셋 편집 다이얼로그
├── app_drawer_content.dart   # 앱 서랍 (그리드, 페이징)
├── app_drawer.dart           # 앱 서랍 애니메이션 래퍼
├── app_selection_screen.dart # 앱 선택 화면
├── native_service.dart       # Native MethodChannel 통신
└── connectivity_service.dart # 네트워크 상태 관리
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

## UI 레이아웃

```
┌─────────────────────────────────────────────────────────────┐
│                    HomeScreen (가로 모드)                    │
├────────┬────────────────────────────────────────────────────┤
│  Dock  │              Split PIP Area                        │
│        │  ┌────────────────┬────────────────────────┐       │
│  [네트] │  │                │                        │       │
│  [워크] │  │    PIP 1       │        PIP 2           │       │
│        │  │   (좌측)       │       (우측)            │       │
│ ──────│  │                │                        │       │
│ [P1]  │  │                │                        │       │
│ [P2]  │  └────────────────┴────────────────────────┘       │
│ [P3]  │       ↑ 비율 조절 구분선 (드래그)                    │
│ ──────│                                                     │
│ [앱]  │  앱서랍 오버레이 (하단에서 슬라이드)                  │
└────────┴────────────────────────────────────────────────────┘
```

## 프리셋 시스템

```dart
// preset_service.dart
class SplitRatio {
  final int left;   // 30 ~ 70
  final int right;  // 70 ~ 30
  
  static const options = [
    SplitRatio(30, 70),  // 30:70
    SplitRatio(35, 65),
    SplitRatio(40, 60),
    SplitRatio(45, 55),
    SplitRatio(50, 50),  // 기본
    SplitRatio(55, 45),
    SplitRatio(60, 40),
    SplitRatio(65, 35),
    SplitRatio(70, 30),  // 70:30
  ];
}

class PresetConfig {
  final double leftRatio;     // 0.3 ~ 0.7
  final PipAppConfig pip1;    // 좌측 앱 설정
  final PipAppConfig pip2;    // 우측 앱 설정
}
```

## VirtualDisplay 설정

| 설정 | 값 | 설명 |
|------|-----|------|
| DPI | 160 | 고정 (자연스러운 스케일) |
| 해상도 | 뷰 크기 × devicePixelRatio | 실제 픽셀로 렌더링 |
| 스케일 | 0.5 ~ 1.5 | 앱별 개별 스케일 조절 |
