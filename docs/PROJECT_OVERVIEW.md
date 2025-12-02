# CarrotPlay - 프로젝트 개요

## 앱 소개

**CarrotPlay**는 Android 차량용 인포테인먼트 시스템(IVI)을 위한 **듀얼 PIP 런처**입니다.
- 두 개의 앱을 동시에 실행하고 비율 조절 가능
- Apple CarPlay 스타일의 모던하고 프리미엄한 UI
- **Android 시스템 앱**으로 동작하여 시스템 권한 사용
- **Kotlin + Jetpack Compose**로 완전 네이티브 구현 (Flutter 완전 제거)

---

## 문서 구조

| 문서 | 내용 | 언제 참조 |
|------|------|----------|
| **PROJECT_OVERVIEW.md** | 프로젝트 개요 (이 문서) | 처음 프로젝트 이해할 때 |
| **[ARCHITECTURE.md](./ARCHITECTURE.md)** | 폴더 구조, 컴포넌트 구조, 확장 가이드 | 코드 추가/수정할 때 |
| **[UI_UX_GUIDELINES.md](./UI_UX_GUIDELINES.md)** | 디자인 토큰, 컴포넌트 | UI 작업할 때 |
| **[BUILD_GUIDE.md](./BUILD_GUIDE.md)** | 빌드, 서명, 설치 | 빌드/배포할 때 |

---

## 핵심 기능

### 1. Split PIP 시스템
- 화면을 **두 개의 PIP 영역**으로 분할
- 각 PIP에서 **서로 다른 앱을 동시 실행**
- **VirtualDisplay + AppViewSurface**를 사용하여 앱을 별도 디스플레이에서 렌더링
- `VIRTUAL_DISPLAY_FLAG_TRUSTED` (0x400) 플래그로 신뢰된 디스플레이 생성

### 2. 비율 조절
- 좌우 비율 자유롭게 조절 (20% ~ 80%)
- **프리셋 3개**: 3:7, 5:5, 7:3
- **Divider 드래그**로 미세 조절 (5% 단위 스냅)
- 부드러운 **애니메이션 전환** (tween 300ms)

### 3. Dock (좌측 영역 72dp)
- **시계 표시**: 상단
- **프리셋 버튼 3개**: 중앙 (현재 비율에 따라 활성화)
- **앱서랍 버튼**: 하단

### 4. 앱서랍 (PIP 내부 오버레이)
- **AppDrawerInPip**: 제스처바 위로 스와이프 또는 중앙 버튼으로 열기
- **슬라이드 + 페이드 애니메이션**: 아래→위로 등장 (spring 기반)
- 앱 선택 시 **해당 PIP의 VirtualDisplay에서 실행**
- 앱 실행 중에는 중앙 앱서랍 버튼 숨김

### 5. 제스처바 (각 PIP 하단 28dp) - 4방향 축 고정 애니메이션
| 제스처 | 동작 | 비고 |
|--------|------|------|
| **← 좌로 스와이프** | 뒤로가기 (Back Key 주입) | threshold 30dp |
| **↑ 위로 스와이프** | 앱서랍 표시 | 슬라이드 애니메이션 |
| **→ 우로 스와이프** | (예약) | - |
| **↓ 아래로 스와이프** | (예약) | - |

**축 고정 방식**: 드래그 시작 후 수평/수직 방향이 결정되면 해당 축으로만 인디케이터가 움직임

### 6. 다양한 해상도 대응
- **메인 타겟**: 현대/기아 5W (10.25인치, 1920x720)
- 향후 다른 차량 디스플레이 확장 예정
- VirtualDisplay 해상도 동적 조절 (Surface 크기 기반)
- dp 단위 UI 스케일링

---

## 화면 구조

```
┌──────────────────────────────────────────────────────────────┐
│                      1920 x 720 px                           │
├──────┬───────────────────────────────────────────────────────┤
│      │  padding: 8dp                                         │
│ Dock │  ┌──────────────────┐ ┌──┐ ┌──────────────────────┐   │
│ 72dp │  │                  │ │  │ │                      │   │
│      │  │     PIP 1        │ │Di│ │       PIP 2          │   │
│시계  │  │  (VirtualDisplay)│ │vi│ │   (VirtualDisplay)   │   │
│      │  │  + 앱서랍 오버레이 │ │de│ │   + 앱서랍 오버레이    │   │
│프리셋│  └──────────────────┘ │r │ └──────────────────────┘   │
│ 1/2/3│  ┌──────────────────┐ │  │ ┌──────────────────────┐   │
│      │  │   GestureBar     │ └──┘ │     GestureBar       │   │
│앱서랍│  │  (4방향 애니메이션)│      │   (4방향 애니메이션)   │   │
│      │  └──────────────────┘      └──────────────────────┘   │
└──────┴───────────────────────────────────────────────────────┘
```

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| **UI Framework** | Jetpack Compose |
| **Language** | Kotlin 1.9+ |
| **compileSdk** | 34 |
| **minSdk / targetSdk** | 29 / 34 |
| **Java** | JDK 17 |
| **Hidden API** | Stub JAR (compileOnly) |
| **패키지명** | `android.test.settings` |
| **서명** | AOSP 플랫폼 키 (platform.p12) |

### 이전 버전 (Archive)
- Flutter + Kotlin (하이브리드)
- MethodChannel로 통신
- Z-order 충돌 문제 있음

### 현재 버전 (dev/main)
- **Kotlin + Jetpack Compose**
- 단일 언어, 네이티브 직접 호출
- Z-order 문제 없음

---

## 시스템 권한

| 권한 | 용도 | 획득 방법 |
|------|------|----------|
| `INJECT_EVENTS` | 터치/키 이벤트 주입 | 플랫폼 서명 |
| `MANAGE_ACTIVITY_TASKS` | 태스크 관리 | 플랫폼 서명 |
| `ADD_TRUSTED_DISPLAY` | VirtualDisplay (Trusted) | 플랫폼 서명 |
| `SYSTEM_ALERT_WINDOW` | 오버레이 | 런타임 요청 |
| `CAPTURE_VIDEO_OUTPUT` | VirtualDisplay 생성 | 플랫폼 서명 |

---

## Hidden API 사용 목록

| API | 클래스 | 용도 |
|-----|--------|------|
| `getInstance()` | `InputManager` | InputManager 인스턴스 획득 |
| `injectInputEvent()` | `InputManager` | 터치/키 이벤트 주입 |
| `getWindowManagerService()` | `WindowManagerGlobal` | IWindowManager 획득 |
| `syncInputTransactions()` | `IWindowManager` | 입력 동기화 |
| `getService()` | `ServiceManager` | 시스템 서비스 획득 |

---

## 빠른 시작

### 빌드 & 설치 (PowerShell)
```powershell
cd D:\nMirror\CarrotPlay

# 빌드
./gradlew assembleDebug

# 서명
& "C:\Users\jomin\AppData\Local\Android\sdk\build-tools\35.0.0\apksigner.bat" sign `
    --ks "D:\nMirror\tools\aosp_keys\platform.p12" `
    --ks-pass pass:android `
    --ks-key-alias platform `
    --out "app\build\outputs\apk\debug\carrotplay-signed.apk" `
    "app\build\outputs\apk\debug\app-debug.apk"

# 설치 & 실행
adb install -r "app\build\outputs\apk\debug\carrotplay-signed.apk"
adb shell am start -n android.test.settings/.MainActivity
```

자세한 내용은 [BUILD_GUIDE.md](./BUILD_GUIDE.md) 참조
