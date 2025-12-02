# CarrotPlay - UI/UX 가이드라인

## 1. 디자인 철학

### 핵심 원칙
| 원칙 | 설명 |
|------|------|
| **Premium & Modern** | Apple CarPlay와 유사한 고급스럽고 현대적인 인터페이스 |
| **Consistency** | 일관된 색상, 타이포그래피, 여백, 코너 라운드 |
| **Vibrancy** | 어두운 배경 + 선명한 포인트 컬러 + 블러 효과 |
| **Tactile Feedback** | 즉각적인 시각적/촉각적 피드백 |

### 디자인 목표
- **Touch-First**: 최소 44x44dp 터치 영역
- **Glanceable**: 빠르게 정보 파악 가능한 시각적 계층
- **Delightful**: 부드러운 애니메이션과 미세한 인터랙션

---

## 2. 컬러 팔레트 (AppColors)

### 브랜드 컬러
| 이름 | 색상 코드 | 용도 |
|------|----------|------|
| **Carrot Orange** | `#FF6B00` | Primary Accent (활성 버튼, 제스처바 활성) |
| **Midnight Black** | `#000000` | 앱 전체 배경 |
| **Glass Grey** | `#1C1C1E` | 카드, 독, 모달 배경 |

### 텍스트 컬러
| 이름 | 색상 코드 | 용도 |
|------|----------|------|
| **White** | `#FFFFFF` | 주요 텍스트, 활성 아이콘 |
| **Steel Grey** | `#8E8E93` | 보조 텍스트, 비활성 아이콘 |

### 반투명 컬러
| 이름 | 색상 코드 | 용도 |
|------|----------|------|
| **WhiteAlpha10** | `#FFFFFF` 10% | 비활성 버튼 배경 |
| **WhiteAlpha30** | `#FFFFFF` 30% | 제스처바 인디케이터, Divider |

### Compose 구현
```kotlin
object AppColors {
    val CarrotOrange = Color(0xFFFF6B00)
    val MidnightBlack = Color(0xFF000000)
    val GlassGrey = Color(0xFF1C1C1E)
    val White = Color(0xFFFFFFFF)
    val SteelGrey = Color(0xFF8E8E93)
    val WhiteAlpha10 = Color(0x1AFFFFFF)
    val WhiteAlpha30 = Color(0x4DFFFFFF)
}
```

---

## 3. 타이포그래피 (AppTypography)

| 스타일 | 크기 | 굵기 | 용도 |
|--------|------|------|------|
| **Header1** | 24sp | Bold | 메인 타이틀 |
| **Header2** | 20sp | SemiBold | 섹션 헤더 |
| **Header3** | 18sp | Medium | 카드 타이틀 |
| **BodyLarge** | 16sp | Regular | 본문 |
| **BodyMedium** | 14sp | Regular | 보조 본문 |
| **Caption** | 12sp | Regular | 부가 설명 |
| **Button** | 16sp | SemiBold | 버튼 텍스트 |

---

## 4. 레이아웃 치수 (AppDimens)

### 타겟 디스플레이

| 플랫폼 | 크기 | 해상도 | 밀도 | 상태 |
|--------|------|--------|------|------|
| **현대/기아 5W** | **10.25인치** | **1920x720** | **~160dpi** | 🎯 메인 |

### 레이아웃 구조
```
┌──────┬─────────────────────────────────────────────────────┐
│      │  PipPadding: 8dp                                    │
│ Dock │  ┌─────────────────┐  ┌──┐  ┌─────────────────┐    │
│ 72dp │  │                 │  │Di│  │                 │    │
│      │  │     PIP 1       │  │vi│  │     PIP 2       │    │
│      │  │  (SurfaceView)  │  │de│  │  (SurfaceView)  │    │
│      │  │                 │  │r │  │                 │    │
│      │  │                 │  │24│  │                 │    │
│      │  ├─────────────────┤  │dp│  ├─────────────────┤    │
│      │  │   GestureBar    │  │  │  │   GestureBar    │    │
│      │  │     28dp        │  │  │  │     28dp        │    │
│      │  └─────────────────┘  └──┘  └─────────────────┘    │
└──────┴─────────────────────────────────────────────────────┘
```

### 주요 치수 상수

| 이름 | 값 | 용도 |
|------|-----|------|
| **DockWidth** | 72dp | 좌측 독 너비 |
| **MinTouchTarget** | 44dp | 최소 터치 영역 |
| **DividerWidth** | 24dp | 비율 조절 바 터치 영역 |
| **DividerHandleWidth** | 4dp | 비율 조절 손잡이 너비 |
| **DividerHandleHeight** | 40dp | 비율 조절 손잡이 높이 |
| **GestureBarHeight** | 28dp | 하단 제스처 영역 높이 |
| **GestureBarIndicatorWidth** | 64dp | 인디케이터 길이 |
| **GestureBarIndicatorHeight** | 5dp | 인디케이터 두께 |
| **PipPadding** | 8dp | PIP 영역 외곽 여백 |

### 코너 라운드

| 이름 | 값 | 용도 |
|------|-----|------|
| **RadiusLarge** | 18dp | 카드, 모달 |
| **RadiusMedium** | 12dp | 버튼, 내부 요소 |
| **RadiusSmall** | 8dp | 작은 요소 |

### 패딩

| 이름 | 값 | 용도 |
|------|-----|------|
| **PaddingLarge** | 24dp | 화면 외곽 여백 |
| **PaddingMedium** | 16dp | 컴포넌트 간 간격 |
| **PaddingSmall** | 8dp | 내부 요소 간격 |

---

## 5. 애니메이션 가이드

### 5.1 제스처바 애니메이션 (4방향 축 고정)

```kotlin
// 오프셋 애니메이션 - 터치 중 부드럽게, 해제 시 빠르게 복귀
val animatedOffset by animateFloatAsState(
    targetValue = if (isPressed) normalizedOffset else 0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = if (isPressed) Spring.StiffnessLow else Spring.StiffnessMedium
    )
)

// 스케일 애니메이션 - 터치 시 1.2배 확대
val indicatorScale by animateFloatAsState(
    targetValue = if (isPressed) 1.2f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
)

// 방향에 따른 오프셋
val offsetX = when { dragDirection == 0 -> animatedOffset * 40f else -> 0f }
val offsetY = when { dragDirection == 1 -> animatedOffset * 25f else -> 0f }
```

### 5.2 앱서랍 애니메이션

```kotlin
AnimatedVisibility(
    visible = showAppDrawer,
    enter = slideInVertically(
        initialOffsetY = { it },  // 아래에서 위로
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    ) + fadeIn(animationSpec = tween(200)),
    exit = slideOutVertically(
        targetOffsetY = { it },  // 위에서 아래로
        animationSpec = tween(200)
    ) + fadeOut(animationSpec = tween(150))
)
```

### 5.3 비율 전환 애니메이션

```kotlin
val displayRatio = if (isDragging) {
    targetRatio  // 드래그 중에는 애니메이션 없이 즉시
} else {
    animateFloatAsState(
        targetValue = targetRatio,
        animationSpec = tween(durationMillis = 300)
    ).value
}
```

### 5.4 애니메이션 규칙

| 상황 | Duration | Spec | 비고 |
|------|----------|------|------|
| 비율 전환 | 300ms | tween | 프리셋 클릭 시 |
| 제스처바 인디케이터 | spring | DampingRatioMediumBouncy | 터치 피드백 |
| 앱서랍 등장 | spring | StiffnessMedium | 슬라이드 + 페이드 |
| 앱서랍 퇴장 | 200ms | tween | 빠르게 사라짐 |
| 버튼 페이드 | 200-300ms | tween | 앱서랍 버튼 |

---

## 6. 시스템 제스처 배제

### API 요구사항
- Android 10 (API 29) 이상 필수
- `systemGestureExclusionRects` API 사용

### 배제 영역

| 영역 | 크기 | 설명 |
|------|------|------|
| **하단 전체** | 60px | 모든 제스처바 영역 커버 |
| **제스처바 주변** | ±20px | 각 제스처바 개별 설정 |

### 구현 예시
```kotlin
// HomeScreen 레벨
.onGloballyPositioned { coordinates ->
    val size = coordinates.size
    val exclusionRect = Rect(0, size.height - 60, size.width, size.height)
    view.systemGestureExclusionRects = listOf(exclusionRect)
}

// GestureBar 레벨
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

## 7. 개발 체크리스트

### 새 화면 추가 시
- [ ] 배경색 `MidnightBlack` 설정
- [ ] 모든 터치 요소 최소 44dp
- [ ] AppColors, AppDimens, AppTypography 사용 (하드코딩 금지)
- [ ] 애니메이션 300ms 이하

### 터치 요소
- [ ] 터치 피드백 적용 (색상 변경, 스케일 등)
- [ ] 인접 요소 간 8dp 이상 간격

### 제스처 처리
- [ ] `detectDragGestures` 사용
- [ ] threshold 설정 (기본 30dp)
- [ ] 방향 결정 로직 (수평/수직)
- [ ] `systemGestureExclusionRects` 설정

### 애니메이션
- [ ] spring 기반 (자연스러운 피드백)
- [ ] tween 기반 (정확한 타이밍 필요 시)
- [ ] AnimatedVisibility 활용 (등장/퇴장)
