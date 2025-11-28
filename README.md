# CarrotPlay

Android 차량용 런처 - VirtualDisplay 기반 분할 PIP 앱 실행

## 특징

- 🚗 차량 환경에 최적화된 가로 모드 UI
- 📱 VirtualDisplay 기반 분할 PIP (2개 앱 동시 실행)
- 📐 비율 조절 가능 (30:70 ~ 70:30, 5% 단위)
- 💾 프리셋 시스템 (3개 프리셋, 앱/비율/스케일 저장)
- 👆 실시간 터치 주입 (InputManager Hidden API)
- 🔧 시스템 앱 권한으로 완전한 제어

## 스크린샷

```
┌────────┬─────────────────────────────────────┐
│  Dock  │  PIP 1 (40%)  │    PIP 2 (60%)      │
│ ────── │               │                     │
│  [P1]  │   앱 A        │      앱 B           │
│  [P2]  │               │                     │
│  [P3]  │               │                     │
│ ────── │               │                     │
│  [앱]  │               │                     │
└────────┴─────────────────────────────────────┘
```

## 요구사항

- Android 10+ (API 29+)
- **AOSP 플랫폼 키로 서명 필수**
- Root 권한 (Magisk 권장)

## 주요 기능

### 분할 PIP
- 좌우 2개 VirtualDisplay에서 앱 동시 실행
- 구분선 드래그로 비율 조절 (1초 롱프레스 후 활성화)
- 5% 단위 스냅 (30:70 ~ 70:30)

### 프리셋
- 3개 프리셋 슬롯
- 탭: 프리셋 실행, 롱프레스: 편집
- 저장 항목: 좌우 비율, PIP 1/2 앱, 앱별 스케일

### 앱 서랍
- 2행 5열 그리드, 페이지네이션
- 상단 제스처 바로 슬라이드 닫기
- 앱/프리셋 선택 시 자동 닫힘

## 빌드

자세한 내용은 [BUILD_GUIDE.md](docs/BUILD_GUIDE.md) 참조

```powershell
# Flutter 경로: C:\flutter
# 빌드
C:\flutter\bin\flutter build apk --debug

# 서명
apksigner sign --ks platform.p12 --ks-pass pass:android --out signed.apk app-debug.apk

# 설치
adb install signed.apk
```

## 문서

- [빌드 가이드](docs/BUILD_GUIDE.md)
- [아키텍처](docs/ARCHITECTURE.md)

## 라이선스

Private - 비공개 프로젝트
