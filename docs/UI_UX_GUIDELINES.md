# Project Fresh Carrot: UI/UX Guidelines

이 문서는 **CarrotPlay (Project Fresh Carrot)** 의 디자인 철학, 스타일 가이드, 그리고 컴포넌트 사용법을 정의합니다. 향후 유지보수 및 기능 추가 시 이 가이드라인을 준수하여 일관된 사용자 경험을 제공해야 합니다.

## 1. 디자인 철학 (Design Philosophy)

### 1.1. 핵심 원칙
*   **Premium & Modern**: Apple CarPlay와 유사한 고급스럽고 현대적인 차량용 인터페이스를 지향합니다.
*   **Consistency**: 모든 화면에서 일관된 색상, 타이포그래피, 여백, 코너 라운드(Squircle)를 유지합니다.
*   **Vibrancy**: 어두운 배경 위에서 선명한 포인트 컬러(Carrot Orange)와 블러 효과(Glassmorphism)를 사용하여 생동감을 줍니다.
*   **Tactile Feedback**: 터치 인터랙션 시 시각적(애니메이션) 및 촉각적(햅틱) 피드백을 즉각적으로 제공합니다.

### 1.2. 디자인 목표
*   **Touch-First**: 차량 환경에서 쉽게 조작할 수 있도록 최소 44x44px 터치 영역 보장
*   **Glanceable**: 운전 중 빠르게 정보를 파악할 수 있도록 명확한 시각적 계층 구조
*   **Delightful**: 부드러운 애니메이션과 미세한 인터랙션으로 즐거운 사용 경험 제공

## 2. 디자인 시스템 (Design System)

모든 디자인 토큰은 `lib/theme/` 디렉토리 내에 정의되어 있습니다. **하드코딩된 값을 절대 사용하지 말고** 정의된 상수를 사용하세요.

### 2.1. 컬러 팔레트 (Colors) - `app_colors.dart`

#### 브랜드 컬러 (Brand Colors)
| 이름 | 색상 코드 | 용도 | 사용 예시 |
| --- | --- | --- | --- |
| **Carrot Orange** | `#FF6B00` | Primary Accent | 활성 버튼, 선택된 탭, 슬라이더 |
| **Midnight Black** | `#000000` | Background | 앱 전체 배경 |
| **Glass Grey** | `#1C1C1E` | Surface | 카드, 독, 모달 배경 (투명도 0.3~0.5) |

#### 텍스트 컬러 (Text Colors)
| 이름 | 색상 코드 | 용도 |
| --- | --- | --- |
| **White** | `#FFFFFF` | 주요 텍스트, 활성 아이콘 |
| **Steel Grey** | `#8E8E93` | 보조 텍스트, 비활성 아이콘 |

#### 기능 컬러 (Functional Colors)
| 이름 | 색상 코드 | 용도 |
| --- | --- | --- |
| **Info Blue** | `#0A84FF` | 정보 표시, PIP 1 강조 |
| **Success Green** | `#30D158` | 성공 상태, PIP 2 강조 |
| **Error Red** | `#FF453A` | 에러, 경고, 삭제 확인 |

**사용 예시**:
```dart
// ✅ 올바른 사용
Container(color: AppColors.carrotOrange)

// ❌ 잘못된 사용
Container(color: Color(0xFFFF6B00))
```

### 2.2. 타이포그래피 (Typography) - `app_text_styles.dart`

폰트는 기본적으로 **시스템 폰트**(San Francisco / Roboto)를 사용하며, 가독성을 최우선으로 합니다.

| 스타일 | 크기 | 굵기 | 용도 | 사용 예시 |
| --- | --- | --- | --- | --- |
| **Header1** | 24px | Bold | 메인 타이틀 | 화면 제목 |
| **Header2** | 20px | SemiBold | 섹션 헤더 | 프리셋 에디터 제목 |
| **Header3** | 18px | Medium | 카드 타이틀 | PIP 카드 제목 |
| **BodyLarge** | 16px | Regular | 본문 | 일반 텍스트 |
| **BodyMedium** | 14px | Regular | 보조 본문 | 설명 텍스트 |
| **Caption** | 12px | Regular | 부가 설명 | 라벨, 힌트 |
| **Label** | 12px | Medium | 강조 라벨 | 버튼 내 텍스트 |
| **Button** | 16px | SemiBold | 버튼 텍스트 | 주요 액션 버튼 |

**사용 예시**:
```dart
Text('Preset 1', style: AppTextStyles.header2)
```

### 2.3. 레이아웃 및 치수 (Dimensions) - `app_dimens.dart`

#### 코너 라운드 (Corner Radius)
*   `radiusLarge` (18.0): 카드, 모달, 큰 버튼 → **Squircle 형태 지향**
*   `radiusMedium` (12.0): 작은 버튼, 내부 요소
*   `radiusSmall` (8.0): 아주 작은 요소

#### 패딩 (Padding)
*   `paddingLarge` (24.0): 화면 외곽 여백
*   `paddingMedium` (16.0): 컴포넌트 간 간격
*   `paddingSmall` (8.0): 내부 요소 간격

#### 레이아웃 상수 (Layout Constants)
*   `dockWidth` (80.0): 왼쪽 독 너비
*   `minTouchTarget` (44.0): 최소 터치 영역 (차량 환경 고려)

**사용 예시**:
```dart
Container(
  padding: EdgeInsets.all(AppDimens.paddingMedium),
  decoration: BoxDecoration(
    borderRadius: BorderRadius.circular(AppDimens.radiusLarge),
  ),
)
```

## 3. 핵심 컴포넌트 (Core Components)

### 3.1. 애니메이션 위젯 (Animation Widgets)

#### BouncyButton (`lib/widgets/animations/bouncy_button.dart`)
**목적**: 모든 터치 가능한 요소에 일관된 촉각 피드백 제공

**특징**:
- 터치 시 크기가 `0.95`배로 줄어들었다가 튕기듯 복원
- `HapticFeedback.lightImpact` 자동 실행
- `Curves.easeInOut` 커브 사용 (150ms)

**사용법**:
```dart
BouncyButton(
  onPressed: () => print('Tapped!'),
  child: Icon(Icons.home),
)
```

**적용 대상**: 모든 버튼, 아이콘, 카드, 리스트 아이템

#### AnimatedGlassCard (`lib/widgets/animations/animated_glass_card.dart`)
**목적**: 글라스모피즘 효과가 적용된 기본 컨테이너

**특징**:
- `BackdropFilter` 블러 효과 (sigma: 10.0)
- 반투명 배경 (Glass Grey + opacity 0.3)
- 선택 상태에 따라 테두리 색상 변경 (Carrot Orange ↔ White 10%)
- `AnimatedContainer`로 부드러운 전환 (200ms)

**사용법**:
```dart
AnimatedGlassCard(
  isSelected: true,
  child: Text('Content'),
)
```

#### FadeSlideTransition (`lib/widgets/animations/fade_slide_transition.dart`)
**목적**: 화면/모달 전환용 복합 애니메이션

**특징**:
- Fade (투명도) + Slide (위치) 동시 적용
- `Curves.easeOutQuart` 커브 (300ms)
- 자연스러운 진입/퇴장 효과

**사용법**:
```dart
Navigator.push(
  context,
  PageRouteBuilder(
    pageBuilder: (_, __, ___) => NextScreen(),
    transitionsBuilder: (_, animation, __, child) =>
      FadeSlideTransition(animation: animation, child: child),
  ),
)
```

### 3.2. 공통 UI 위젯 (Common UI Widgets)

#### CarrotSlider (`lib/widgets/common/carrot_slider.dart`)
**목적**: 브랜드 아이덴티티가 적용된 커스텀 슬라이더

**특징**:
- Carrot Orange 활성 트랙
- 두꺼운 트랙 (6px)
- 큰 썸 (16px 반지름)

**사용법**:
```dart
CarrotSlider(
  value: 0.5,
  min: 0.0,
  max: 1.0,
  divisions: 10,
  onChanged: (value) => setState(() => _value = value),
)
```

#### AppIconWrapper (`lib/widgets/common/app_icon_wrapper.dart`)
**목적**: 모든 앱 아이콘을 Squircle 형태로 통일

**특징**:
- 둥근 모서리 사각형 (radiusMedium)
- 폴백 아이콘 지원 (Icons.android)
- 일관된 크기 및 스타일

**사용법**:
```dart
AppIconWrapper(
  iconData: app.icon,  // Uint8List?
  size: 64,
  radius: AppDimens.radiusMedium,
)
```

#### PresetCard (`lib/widgets/common/preset_card.dart`)
**목적**: 프리셋 선택용 인터랙티브 카드

**특징**:
- `AnimatedGlassCard` + `BouncyButton` 조합
- 타이틀, 설명, 아이콘 표시
- 선택 상태 시각화

### 3.3. 레이아웃 위젯 (Layout Widgets)

#### GlassDock (`lib/widgets/dock/glass_dock.dart`)
**목적**: 왼쪽 고정 네비게이션 바

**구성 요소**:
- `DigitalClock`: 실시간 시계
- 네트워크 상태 아이콘
- 프리셋 버튼 (1~4)
- 앱 서랍 버튼

**특징**:
- `BackdropFilter` 블러 효과
- 선택된 프리셋은 Carrot Orange 강조
- 모든 버튼에 `BouncyButton` 적용

#### SplitViewArea (`lib/widgets/pip/split_view_area.dart`)
**목적**: 두 개의 PIP를 분할하여 표시

**구성 요소**:
- 두 개의 `PipView` (좌/우)
- `_RatioResizer`: 비율 조절 바

**Resizer 특징**:
- 길게 눌러 활성화 (500ms)
- 호버/드래그 상태 시각 피드백
- 햅틱 피드백 (활성화, 비활성화 시)
- 0.3~0.7 범위로 제한, 0.05 단위로 스냅

## 4. 개발 가이드라인 (Development Guidelines)

### 4.1. 새로운 화면 추가 시

**필수 체크리스트**:
1. ✅ `Scaffold` 배경색을 `Colors.black`으로 설정
2. ✅ `GlassDock`을 포함하여 전체 레이아웃 구성
3. ✅ 컨텐츠는 `AnimatedGlassCard` 안에 배치
4. ✅ 모든 터치 요소에 `BouncyButton` 적용
5. ✅ 디자인 토큰(`AppColors`, `AppTextStyles`, `AppDimens`) 사용

**예시 코드**:
```dart
class NewScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Row(
        children: [
          GlassDock(/* ... */),
          Expanded(
            child: AnimatedGlassCard(
              child: /* 컨텐츠 */,
            ),
          ),
        ],
      ),
    );
  }
}
```

### 4.2. 애니메이션 적용 규칙

| 상황 | 사용 위젯/방법 | Duration | Curve |
| --- | --- | --- | --- |
| 버튼/카드 터치 | `BouncyButton` | 150ms | `easeInOut` |
| 화면 전환 | `FadeSlideTransition` | 300ms | `easeOutQuart` |
| 상태 변경 | `AnimatedContainer` | 200ms | `easeOutQuart` |
| 투명도 변경 | `AnimatedOpacity` | 200ms | `easeOutQuart` |

**주의사항**:
- 애니메이션 duration은 300ms를 초과하지 않도록 (차량 환경 고려)
- 과도한 애니메이션은 피하고, 의미 있는 곳에만 적용
- 저사양 기기에서도 60fps 유지 확인

### 4.3. 아이콘 사용 규칙

**앱 아이콘**:
- ✅ 항상 `AppIconWrapper` 사용
- ✅ Squircle 형태 유지
- ✅ 일관된 크기 (48px, 56px, 64px 중 선택)

**시스템 아이콘**:
- ✅ `CupertinoIcons` 또는 `Icons` (Material Rounded) 사용
- ✅ 둥근 스타일 선호
- ✅ 최소 크기 24px

**예시**:
```dart
// 앱 아이콘
AppIconWrapper(iconData: app.icon, size: 64)

// 시스템 아이콘
Icon(CupertinoIcons.home, size: 24, color: AppColors.white)
```

### 4.4. 터치 영역 최적화

**차량 환경 고려사항**:
- 모든 터치 요소는 최소 `44x44px` 확보
- 중요한 버튼은 `56x56px` 이상 권장
- 인접한 터치 요소 간 최소 `8px` 간격 유지

**검증 방법**:
```dart
// 터치 영역 시각화 (디버그 모드)
MaterialApp(
  debugShowMaterialGrid: true,
  showPerformanceOverlay: true,
)
```

## 5. 브랜딩 및 앱 진입 (Branding & Entry)

### 5.1. 앱 아이콘 (App Icon)

**디자인 규칙**:
- **배경**: Carrot Orange 그라데이션 (밝은 오렌지 → 어두운 오렌지)
- **심볼**: White, 플랫 디자인 (글라스 효과 없음)
- **형태**: Squircle (둥근 모서리 사각형)
- **컨셉**: Carrot + Play 버튼의 조합

**파일 위치**: `assets/icon.png` (1024x1024px PNG)

**생성 명령어**:
```bash
dart run flutter_launcher_icons
```

**설정** (`pubspec.yaml`):
```yaml
flutter_icons:
  android: "launcher_icon"
  ios: true
  image_path: "assets/icon.png"
  adaptive_icon_background: "#000000"
  adaptive_icon_foreground: "assets/icon.png"
```

### 5.2. 스플래시 스크린 (Splash Screen)

**디자인 규칙**:
- **배경**: Midnight Black (#000000)
- **로고**: 앱 아이콘, 화면 중앙 배치
- **지속 시간**: 앱 로딩 완료 시까지 (약 2~3초)

**생성 명령어**:
```bash
dart run flutter_native_splash:create
```

**설정** (`pubspec.yaml`):
```yaml
flutter_native_splash:
  color: "#000000"
  image: "assets/icon.png"
  android_12:
    image: "assets/icon.png"
    icon_background_color: "#000000"
```

## 6. 유지보수 및 확장 (Maintenance & Extension)

### 6.1. 디자인 토큰 변경 시
1. `lib/theme/` 파일 수정
2. 앱 전체에 자동 반영 확인
3. 문서 업데이트 (`UI_UX_GUIDELINES.md`)

### 6.2. 새로운 공통 위젯 추가 시
1. `lib/widgets/common/` 또는 적절한 하위 디렉토리에 추가
2. 디자인 토큰 사용 확인
3. 이 문서의 "핵심 컴포넌트" 섹션에 추가
4. 사용 예시 코드 작성

### 6.3. 코드 리뷰 체크리스트
- [ ] 하드코딩된 색상/크기 값 없음
- [ ] 모든 터치 요소에 `BouncyButton` 적용
- [ ] 최소 터치 영역 44px 확보
- [ ] 애니메이션 duration 300ms 이하
- [ ] `AppIconWrapper` 사용 (앱 아이콘)
- [ ] 일관된 코너 라운드 (radiusLarge/Medium)

## 7. 참고 자료 (References)

- [Apple Human Interface Guidelines - CarPlay](https://developer.apple.com/design/human-interface-guidelines/carplay)
- [Material Design 3](https://m3.material.io/)
- [Flutter Animation Best Practices](https://docs.flutter.dev/ui/animations)
- [Glassmorphism Design Trend](https://uxdesign.cc/glassmorphism-in-user-interfaces-1f39bb1308c9)
