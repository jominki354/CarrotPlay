# CarrotPlay

Android 차량용 런처 - VirtualDisplay 기반 PIP(Picture-in-Picture) 멀티태스킹 지원

## 주요 기능

- 🚗 차량 환경에 최적화된 런처 UI
- 📱 VirtualDisplay를 활용한 앱 PIP 모드 실행
- 🎯 시스템 앱 권한으로 터치 이벤트 주입 (AOSP 테스트 키 서명 시)
- 🔄 앱 간 빠른 전환

## 빠른 시작

### 일반 빌드
```bash
flutter build apk --release
```

### 시스템 앱으로 빌드 (AOSP 테스트 키 서명)

시스템 권한(INJECT_EVENTS, ADD_TRUSTED_DISPLAY 등)이 필요한 경우:

**Windows:**
```powershell
.\scripts\build_and_sign.ps1
```

**Linux/macOS:**
```bash
chmod +x scripts/build_and_sign.sh
./scripts/build_and_sign.sh
```

자세한 내용은 [시스템 서명 가이드](docs/SYSTEM_SIGNING_GUIDE.md)를 참조하세요.

## 프로젝트 구조

```
carcar_launcher/
├── lib/                    # Flutter/Dart 코드
│   ├── main.dart
│   ├── screens/
│   └── services/
├── android/                # Android 네이티브 코드
│   └── app/src/main/
│       ├── kotlin/         # MainActivity (VirtualDisplay 관리)
│       └── AndroidManifest.xml
├── scripts/                # 빌드/서명 스크립트
│   ├── build_and_sign.ps1  # Windows
│   └── build_and_sign.sh   # Linux/macOS
├── docs/                   # 문서
│   └── SYSTEM_SIGNING_GUIDE.md
└── tools/                  # (상위 폴더)
    └── aosp_keys/          # AOSP 플랫폼 테스트 키
```

## 요구 사항

- Flutter SDK 3.x+
- Android SDK (API 24+)
- Java 17+

### 시스템 앱 기능 사용 시
- AOSP 테스트 키 기반 기기 또는
- Root 권한 + /system/priv-app 설치

## 라이선스

MIT License

## 제작자

kooingh354
