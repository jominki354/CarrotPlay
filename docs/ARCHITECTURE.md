# CarrotPlay - 아키텍처 설계

## 1. 폴더 구조 (실제 구현)

```
CarrotPlay/
├── app/
│   ├── build.gradle.kts           # Gradle 빌드 설정
│   ├── libs/
│   │   └── hidden-api-stub.jar    # Hidden API Stub (compileOnly)
│   └── src/main/
│       ├── AndroidManifest.xml    # 매니페스트 (시스템 앱 설정)
│       ├── kotlin/android/test/settings/
│       │   ├── MainActivity.kt    # 메인 액티비티 + 모든 UI Composable
│       │   ├── ui/
│       │   │   ├── theme/
│       │   │   │   ├── Color.kt       # 색상 정의 (AppColors)
│       │   │   │   ├── Dimens.kt      # 치수 정의 (AppDimens)
│       │   │   │   ├── Theme.kt       # 테마 설정 (CarrotPlayTheme)
│       │   │   │   └── Typography.kt  # 타이포그래피 (AppTypography)
│       │   │   ├── applist/
│       │   │   │   └── AppListDialog.kt  # 앱 목록 로드 유틸리티
│       │   │   └── pip/
│       │   │       └── PipSurfaceView.kt # PIP용 SurfaceView Composable
│       │   └── virtualdisplay/
│       │       ├── AppViewSurface.kt      # VirtualDisplay + 터치 주입 + 앱 실행
│       │       └── VirtualDisplayManager.kt # VirtualDisplay 캐시 관리
│       └── res/
│           ├── values/
│           │   └── strings.xml
│           └── xml/
│               └── filepaths.xml
├── docs/
│   ├── PROJECT_OVERVIEW.md
│   ├── ARCHITECTURE.md (이 문서)
│   ├── UI_UX_GUIDELINES.md
│   └── BUILD_GUIDE.md
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 2. 핵심 컴포넌트

### 2.1 MainActivity.kt (메인 UI)

**위치**: `app/src/main/kotlin/android/test/settings/MainActivity.kt`

모든 UI Composable이 하나의 파일에 정의됨:

| Composable | 역할 |
|------------|------|
| `HomeScreen` | 최상위 레이아웃 (Dock + PipArea), 시스템 제스처 제외 설정 |
| `Dock` | 좌측 72dp 영역 (시계, 프리셋 버튼, 앱서랍 버튼) |
| `PipArea` | PIP 1 + Divider + PIP 2 레이아웃, 비율 조절 |
| `PipWithGesture` | 개별 PIP 영역 (PipSurfaceView + 앱서랍 오버레이 + GestureBar) |
| `AppDrawerInPip` | PIP 내부 앱서랍 (슬라이드 애니메이션) |
| `GestureBar` | 4방향 축 고정 제스처 처리 (spring 애니메이션) |

### 2.2 AppViewSurface.kt (VirtualDisplay)

**위치**: `app/src/main/kotlin/android/test/settings/virtualdisplay/AppViewSurface.kt`

| 기능 | 설명 |
|------|------|
| VirtualDisplay 생성 | Surface 크기 기반 동적 생성, TRUSTED 플래그 |
| 터치 이벤트 주입 | `InputManager.injectInputEvent()` |
| Back Key 주입 | `KEYCODE_BACK` 이벤트 생성 및 주입 |
| 앱 실행 | `startActivity()` with display target |

### 2.3 VirtualDisplayManager.kt (캐시 관리)

**위치**: `app/src/main/kotlin/android/test/settings/virtualdisplay/VirtualDisplayManager.kt`

| 기능 | 설명 |
|------|------|
| 싱글톤 인스턴스 | VirtualDisplay 캐시 관리 |
| Display ID 매핑 | slotIndex → Display ID |
| 리소스 정리 | `release()` 호출 시 정리 |

### 2.4 Theme 모듈

**위치**: `app/src/main/kotlin/android/test/settings/ui/theme/`

| 파일 | 내용 |
|------|------|
| `Color.kt` | `AppColors` 객체 (CarrotOrange, MidnightBlack 등) |
| `Dimens.kt` | `AppDimens` 객체 (DockWidth, GestureBarHeight 등) |
| `Typography.kt` | `AppTypography` 객체 (Header1, BodyLarge 등) |
| `Theme.kt` | `CarrotPlayTheme` Composable |

### 2.5 Hidden API Stub

**위치**: `app/libs/hidden-api-stub.jar`

| 클래스 | 메서드 | 용도 |
|--------|--------|------|
| `InputManager` | `getInstance()`, `injectInputEvent()` | 입력 주입 |
| `ServiceManager` | `getService()` | 시스템 서비스 접근 |
| `WindowManagerGlobal` | `getWindowManagerService()` | IWindowManager 획득 |
| `IWindowManager` | `syncInputTransactions()` | 입력 동기화 |

---

## 3. 데이터 흐름

### 3.1 앱 실행 흐름

```
사용자 앱 선택 (AppDrawerInPip)
    ↓
onAppSelected(packageName)
    ↓
appViewSurface.launchApp(packageName)
    ↓
startActivity() with display target
    ↓
앱이 VirtualDisplay에서 렌더링
    ↓
Surface에 표시
```

### 3.2 터치 이벤트 흐름

```
사용자 터치 (AppViewSurface)
    ↓
onTouchEvent() 캡처
    ↓
좌표 변환 (Surface → Display)
    ↓
MotionEvent.obtain() 생성
    ↓
InputManager.injectInputEvent()
    ↓
VirtualDisplay로 이벤트 전달
```

### 3.3 제스처바 동작 흐름

```
사용자 드래그 시작 (GestureBar)
    ↓
detectDragGestures() 캡처
    ↓
방향 결정 (수평 vs 수직)
    ↓
dragDirection 상태 설정
    ↓
normalizedOffset 업데이트 (축 고정)
    ↓
animatedOffset 애니메이션 (spring)
    ↓
threshold 도달 시 액션 트리거
    ↓
onSwipeLeft() → sendBackKey()
    또는
onSwipeUp() → showAppDrawer = true
```

---

## 4. State 관리

### 4.1 HomeScreen 레벨

| State | 타입 | 용도 |
|-------|------|------|
| `targetRatio` | `Float` | 목표 비율 (0.2 ~ 0.8) |
| `isDragging` | `Boolean` | Divider 드래그 중 여부 |
| `displayRatio` | `Float` | 애니메이션된 표시 비율 |

### 4.2 PipWithGesture 레벨

| State | 타입 | 용도 |
|-------|------|------|
| `appViewSurface` | `AppViewSurface?` | Surface 참조 |
| `showAppDrawer` | `Boolean` | 앱서랍 표시 여부 |
| `hasApp` | `Boolean` | 앱 실행 중 여부 |

### 4.3 GestureBar 레벨

| State | 타입 | 용도 |
|-------|------|------|
| `isPressed` | `Boolean` | 터치 중 여부 |
| `swiped` | `Boolean` | 스와이프 완료 여부 |
| `dragDirection` | `Int?` | 방향 (null=미정, 0=수평, 1=수직) |
| `normalizedOffset` | `Float` | 정규화된 오프셋 (-1 ~ 1) |

---

## 5. 애니메이션 시스템

### 5.1 제스처바 애니메이션

```kotlin
// 오프셋 애니메이션 (스프링)
val animatedOffset by animateFloatAsState(
    targetValue = if (isPressed) normalizedOffset else 0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = if (isPressed) Spring.StiffnessLow else Spring.StiffnessMedium
    )
)

// 스케일 애니메이션
val indicatorScale by animateFloatAsState(
    targetValue = if (isPressed) 1.2f else 1f,
    animationSpec = spring(...)
)
```

### 5.2 앱서랍 애니메이션

```kotlin
AnimatedVisibility(
    visible = showAppDrawer,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
)
```

### 5.3 비율 전환 애니메이션

```kotlin
val displayRatio = if (isDragging) {
    targetRatio  // 드래그 중에는 즉시 반영
} else {
    animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 300)
    ).value
}
```

---

## 6. 시스템 제스처 제외

### 6.1 HomeScreen 레벨 (하단 60px)

```kotlin
.onGloballyPositioned { coordinates ->
    val size = coordinates.size
    val bottomExclusionHeight = 60
    val exclusionRect = Rect(
        0,
        size.height - bottomExclusionHeight,
        size.width,
        size.height
    )
    view.systemGestureExclusionRects = listOf(exclusionRect)
}
```

### 6.2 GestureBar 레벨 (주변 20px 패딩)

```kotlin
.onGloballyPositioned { coordinates ->
    val padding = 20
    val exclusionRect = Rect(
        (position.x - padding).toInt().coerceAtLeast(0),
        (position.y - padding).toInt().coerceAtLeast(0),
        (position.x + size.width + padding).toInt(),
        (position.y + size.height + padding).toInt()
    )
    view.systemGestureExclusionRects = listOf(exclusionRect)
}
```

---

## 7. 주요 치수 (AppDimens)

| 상수 | 값 | 용도 |
|------|-----|------|
| `DockWidth` | 72.dp | 좌측 Dock 너비 |
| `GestureBarHeight` | 28.dp | 제스처바 높이 |
| `GestureBarIndicatorWidth` | 64.dp | 인디케이터 너비 |
| `GestureBarIndicatorHeight` | 5.dp | 인디케이터 높이 |
| `DividerWidth` | 24.dp | Divider 터치 영역 |
| `DividerHandleWidth` | 4.dp | Divider 손잡이 너비 |
| `DividerHandleHeight` | 40.dp | Divider 손잡이 높이 |
| `PipPadding` | 8.dp | PIP 영역 외곽 패딩 |
| `MinTouchTarget` | 44.dp | 최소 터치 영역 |

---

## 8. 확장 가이드

### 8.1 새 제스처 추가

1. `GestureBar`의 `onDrag` 람다에서 새 방향 처리
2. `onSwipeRight()`, `onSwipeDown()` 등 콜백 추가
3. `PipWithGesture`에서 해당 콜백 연결

### 8.2 새 Hidden API 사용

1. `hidden-api-stub.jar`에 스텁 클래스 추가
2. `AppViewSurface.kt`에서 해당 API 호출
3. 필요한 권한을 `AndroidManifest.xml`에 추가

### 8.3 새 UI 컴포넌트 추가

1. `ui/` 하위에 적절한 패키지 생성
2. Composable 함수 작성
3. `MainActivity.kt`에서 import 후 사용
4. Theme 토큰 사용 (AppColors, AppDimens, AppTypography)
