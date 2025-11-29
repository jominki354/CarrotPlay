# Project Fresh Carrot: UI/UX Remake Walkthrough

CarrotPlay의 UI/UX 리메이크 작업이 완료되었습니다. Apple CarPlay 스타일의 미려하고 일관된 디자인 시스템을 적용하였으며, 모든 인터랙션에 부드러운 애니메이션을 추가하여 사용자 경험을 대폭 개선했습니다.

## 주요 변경 사항 (Key Changes)

### 1. 디자인 시스템 (Design System)
**위치**: `lib/theme/`

- **`app_colors.dart`**: Carrot Orange (#FF6B00), Midnight Black, Glass Grey 등 브랜드 컬러 팔레트 정의
- **`app_text_styles.dart`**: Header1~3, BodyLarge, Caption, Button 등 일관된 타이포그래피 시스템
- **`app_dimens.dart`**: 18px/12px 코너 라운드, 24px/16px 패딩, 44px 최소 터치 영역 등 레이아웃 상수
- **`app_theme.dart`**: Material 3 기반 ThemeData 설정, 커스텀 버튼 및 슬라이더 테마

**효과**: 하드코딩된 값 제거, 일관된 디자인 토큰 사용으로 유지보수성 향상

### 2. 애니메이션 시스템 (Animation System)
**위치**: `lib/widgets/animations/`

- **`bouncy_button.dart`**: 터치 시 0.95배 축소 후 복원되는 스케일 애니메이션 + 햅틱 피드백
- **`fade_slide_transition.dart`**: 화면/모달 전환용 페이드+슬라이드 복합 애니메이션 (300ms, easeOutQuart)
- **`animated_glass_card.dart`**: 선택 상태에 따라 테두리 색상과 배경 불투명도가 변하는 글라스모피즘 카드

**효과**: 모든 인터랙션에 일관된 촉각적 피드백 제공, 프리미엄 느낌 강화

### 3. 공통 UI 컴포넌트 (Common Components)
**위치**: `lib/widgets/common/`

- **`carrot_slider.dart`**: Carrot Orange 색상의 커스텀 슬라이더 (두꺼운 트랙, 큰 썸)
- **`app_icon_wrapper.dart`**: 모든 앱 아이콘을 Squircle 형태로 통일하는 래퍼 위젯
- **`preset_card.dart`**: 프리셋 선택용 글라스 카드 (AnimatedGlassCard + BouncyButton)

### 4. 네비게이션 및 레이아웃 (Navigation & Layout)
**위치**: `lib/widgets/dock/`, `lib/widgets/pip/`

#### GlassDock (왼쪽 사이드바)
- **`digital_clock.dart`**: 실시간 시계 위젯
- **`dock_item.dart`**: BouncyButton 기반 독 아이템
- **`glass_dock.dart`**: BackdropFilter 블러 효과가 적용된 메인 독
  - 시계, 네트워크 상태, 프리셋 버튼(1~4), 앱 서랍 버튼 포함
  - 선택된 프리셋은 Carrot Orange로 강조

#### SplitViewArea (PIP 영역)
- **`split_view_area.dart`**: 두 개의 PipView와 비율 조절 바를 포함하는 분할 뷰
  - 개선된 `_RatioResizer`: 길게 눌러 활성화, 호버/드래그 상태 시각 피드백, 햅틱 피드백

### 5. 화면 리메이크 (Screen Remake)

#### HomeScreen (`lib/home_screen.dart`)
- 기존 `_buildDock`, `_buildSplitPipArea` 메서드 제거
- `GlassDock`와 `SplitViewArea` 위젯으로 대체
- 앱 서랍 오버레이를 중앙 정렬 글라스모픽 컨테이너로 개선
- 레거시 UI 빌딩 메서드 정리 (`_buildPresetButton`, `_buildRatioIndicator` 등)

#### PresetEditor (`lib/preset_editor.dart`)
- 하단 슬라이드 업 바텀 시트 스타일로 전면 개편
- `CarrotSlider`를 사용한 비율 조절
- `AnimatedGlassCard`로 PIP 카드 구성
- `BouncyButton`으로 모든 인터랙션 요소 래핑
- 앱 선택 화면: 그리드 레이아웃 + `AppIconWrapper` 사용

#### AppDrawerContent (`lib/app_drawer_content.dart`)
- 2행 5열 그리드 레이아웃으로 재구성
- `BouncyButton`으로 앱 아이템 래핑
- `AppIconWrapper`로 아이콘 통일
- 페이지 인디케이터 애니메이션 개선 (AnimatedContainer)

### 6. 브랜딩 (Branding)
**위치**: `assets/`, `pubspec.yaml`

- **앱 아이콘**: Carrot 시리즈 아이덴티티 반영 (Carrot Orange 그라데이션 + White 심볼)
  - `flutter_launcher_icons` 패키지로 안드로이드/iOS 아이콘 자동 생성
  - Adaptive Icon 지원 (배경: #000000)
  
- **스플래시 스크린**: 네이티브 레벨 구현
  - `flutter_native_splash` 패키지 사용
  - 배경: Midnight Black (#000000)
  - 중앙에 앱 아이콘 배치
  - Android 12+ 지원

## 기술 스택 (Tech Stack)

- **Flutter SDK**: 3.38.3
- **Dart**: 3.10.1
- **주요 패키지**:
  - `get`: 상태 관리
  - `permission_handler`: 권한 관리
  - `shared_preferences`: 로컬 저장소
  - `flutter_launcher_icons`: 아이콘 생성
  - `flutter_native_splash`: 스플래시 스크린 생성

## 파일 구조 (File Structure)

```
lib/
├── theme/                          # 디자인 시스템
│   ├── app_colors.dart
│   ├── app_text_styles.dart
│   ├── app_dimens.dart
│   └── app_theme.dart
├── widgets/
│   ├── animations/                 # 애니메이션 위젯
│   │   ├── bouncy_button.dart
│   │   ├── fade_slide_transition.dart
│   │   └── animated_glass_card.dart
│   ├── common/                     # 공통 UI 위젯
│   │   ├── carrot_slider.dart
│   │   ├── app_icon_wrapper.dart
│   │   └── preset_card.dart
│   ├── dock/                       # Dock 관련 위젯
│   │   ├── digital_clock.dart
│   │   ├── dock_item.dart
│   │   └── glass_dock.dart
│   └── pip/                        # PIP 관련 위젯
│       └── split_view_area.dart
├── home_screen.dart                # 메인 화면 (리팩토링)
├── preset_editor.dart              # 프리셋 에디터 (리메이크)
└── app_drawer_content.dart         # 앱 서랍 (리메이크)

assets/
└── icon.png                        # 앱 아이콘 원본

docs/
├── UI_UX_GUIDELINES.md             # 디자인 가이드라인
└── UI_REMAKE_WALKTHROUGH.md        # 이 문서
```

## 검증 가이드 (Verification Guide)

### 1. 스플래시 스크린
- 앱 실행 시 Midnight Black 배경에 앱 아이콘이 중앙에 표시되는지 확인
- 2~3초 후 홈 화면으로 자연스럽게 전환되는지 확인

### 2. 홈 화면
- **GlassDock**: 왼쪽 사이드바의 블러 효과 확인
- **시계**: 실시간으로 업데이트되는지 확인
- **프리셋 버튼**: 터치 시 BouncyButton 애니메이션 확인
- **선택 상태**: 현재 프리셋이 Carrot Orange로 강조되는지 확인

### 3. PIP 영역
- **Split View**: 두 개의 PIP가 설정된 비율대로 표시되는지 확인
- **비율 조절 바**: 
  - 길게 눌러 활성화되는지 확인
  - 드래그 시 실시간으로 비율이 변경되는지 확인
  - 햅틱 피드백이 작동하는지 확인

### 4. 프리셋 에디터
- **진입**: 프리셋 버튼 길게 누르기로 바텀 시트가 슬라이드 업되는지 확인
- **비율 선택**: 버튼 클릭 시 시각적 프리뷰가 업데이트되는지 확인
- **앱 선택**: 
  - 그리드 레이아웃이 깔끔하게 표시되는지 확인
  - 앱 아이콘이 Squircle 형태로 통일되었는지 확인
  - BouncyButton 애니메이션 확인
- **스케일 조절**: CarrotSlider로 앱 크기 조절이 부드럽게 작동하는지 확인
- **저장**: 설정이 정상적으로 저장되고 홈 화면에 반영되는지 확인

### 5. 앱 서랍
- **진입**: 독 하단 버튼으로 앱 서랍이 오버레이로 표시되는지 확인
- **그리드**: 2행 5열 레이아웃이 중앙 정렬되었는지 확인
- **페이지 전환**: 스와이프로 페이지 전환 시 부드러운 애니메이션 확인
- **페이지 인디케이터**: 현재 페이지가 명확하게 표시되는지 확인
- **앱 실행**: 앱 선택 시 정상적으로 실행되는지 확인

## 빌드 및 실행 (Build & Run)

```bash
# 의존성 설치
flutter pub get

# 개발 모드 실행
flutter run

# 릴리즈 APK 빌드
flutter build apk --release

# 아이콘 재생성 (필요시)
dart run flutter_launcher_icons

# 스플래시 스크린 재생성 (필요시)
dart run flutter_native_splash:create
```

## 향후 개선 사항 (Future Improvements)

1. **성능 최적화**: 저사양 기기에서 애니메이션 성능 테스트 및 최적화
2. **접근성**: 스크린 리더 지원, 고대비 모드 추가
3. **다크 모드**: 현재 다크 모드 기본이지만, 라이트 모드 옵션 추가 검토
4. **커스터마이징**: 사용자가 Carrot Orange 외 다른 액센트 컬러 선택 가능하도록 확장
