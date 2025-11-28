# CarrotPlay

Android 차량용 런처 - VirtualDisplay 기반 PIP 앱 실행

## 특징

- 🚗 차량 환경에 최적화된 가로 모드 UI
- 📱 VirtualDisplay에서 앱 실행 및 PIP 표시
- 👆 실시간 터치 주입 (InputManager Hidden API)
- 🔧 시스템 앱 권한으로 완전한 제어

## 요구사항

- Android 10+ (API 29+)
- **AOSP 플랫폼 키로 서명 필수**
- Root 권한 (Magisk 권장)

## 빌드

자세한 내용은 [BUILD_GUIDE.md](docs/BUILD_GUIDE.md) 참조

```powershell
# 빌드
flutter build apk --debug

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
