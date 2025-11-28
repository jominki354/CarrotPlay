# CarrotPlay 시스템 앱 서명 가이드

## 개요

CarrotPlay는 VirtualDisplay에서 터치 이벤트를 주입하기 위해 `android.uid.system` 권한이 필요합니다.
이를 위해 AOSP 플랫폼 테스트 키로 서명해야 합니다.

## 빠른 시작 (Windows)

### 1. APK 빌드
```powershell
cd D:\nMirror\carcar_launcher
C:\flutter\bin\flutter build apk --release
```

### 2. 서명 (한 줄 명령어)
```powershell
cd D:\nMirror\tools\aosp_keys
& "C:\Users\jomin\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat" sign --ks platform.p12 --ks-key-alias platform --ks-pass pass:android --key-pass pass:android --out CarrotPlay-system-signed.apk "D:\nMirror\carcar_launcher\build\app\outputs\flutter-apk\app-release.apk"
```

### 3. 설치
```powershell
adb install CarrotPlay-system-signed.apk
```

---

## 상세 설명

### 필요한 파일들 (이미 준비됨)

| 파일 | 위치 | 설명 |
|------|------|------|
| `platform.x509.pem` | `tools/aosp_keys/` | AOSP 플랫폼 인증서 |
| `platform.pk8` | `tools/aosp_keys/` | AOSP 플랫폼 개인키 (DER) |
| `platform-key.pem` | `tools/aosp_keys/` | 개인키 (PEM 형식) |
| `platform.p12` | `tools/aosp_keys/` | PKCS#12 키스토어 |

### 키스토어 정보
- **Alias**: `platform`
- **Password**: `android`
- **Key Password**: `android`

### 서명 인증서 지문
```
SHA-1:   27:19:6E:38:6B:87:5E:76:AD:F7:00:E7:EA:84:E4:C6:EE:E3:3D:FA
SHA-256: C8:A2:E9:BC:CF:59:7C:2F:B6:DC:66:BE:E2:93:FC:13:F2:FC:47:EC:77:BC:6B:2B:0D:52:C1:1F:51:19:2A:B8
```

---

## 키 파일 재생성 방법

키 파일이 없는 경우 다음 순서로 생성합니다:

### 1. AOSP에서 키 다운로드
```powershell
# 인증서 다운로드
Invoke-WebRequest -Uri "https://android.googlesource.com/platform/build/+/refs/heads/master/target/product/security/platform.x509.pem?format=TEXT" -OutFile "platform.x509.pem.b64"

# 개인키 다운로드  
Invoke-WebRequest -Uri "https://android.googlesource.com/platform/build/+/refs/heads/master/target/product/security/platform.pk8?format=TEXT" -OutFile "platform.pk8.b64"
```

### 2. Base64 디코딩
```powershell
# 인증서 디코딩
$b64 = Get-Content platform.x509.pem.b64 -Raw
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($b64.Trim())) | Out-File -Encoding ASCII platform.x509.pem

# 개인키 디코딩
$b64 = Get-Content platform.pk8.b64 -Raw
$bytes = [System.Convert]::FromBase64String($b64.Trim())
[System.IO.File]::WriteAllBytes("platform.pk8", $bytes)
```

### 3. PKCS#12 키스토어 생성
```powershell
# Git에 포함된 OpenSSL 사용
$openssl = "C:\Program Files\Git\usr\bin\openssl.exe"

# pk8를 PEM으로 변환
& $openssl pkcs8 -inform DER -in platform.pk8 -out platform-key.pem -nocrypt

# PKCS#12 생성
& $openssl pkcs12 -export -out platform.p12 -inkey platform-key.pem -in platform.x509.pem -name platform -passout pass:android
```

---

## Linux/macOS 사용자

### 서명 명령어
```bash
cd tools/aosp_keys
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks platform.p12 \
  --ks-key-alias platform \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out CarrotPlay-system-signed.apk \
  ../carcar_launcher/build/app/outputs/flutter-apk/app-release.apk
```

### 키스토어 생성 (OpenSSL)
```bash
# pk8 → PEM
openssl pkcs8 -inform DER -in platform.pk8 -out platform-key.pem -nocrypt

# PKCS#12 생성
openssl pkcs12 -export -out platform.p12 -inkey platform-key.pem -in platform.x509.pem -name platform -passout pass:android
```

---

## 서명 검증

```powershell
& "C:\Users\jomin\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs CarrotPlay-system-signed.apk
```

예상 출력:
```
Signer #1 certificate DN: EMAILADDRESS=android@android.com, CN=Android, OU=Android, O=Android, L=Mountain View, ST=California, C=US
Signer #1 certificate SHA-256 digest: c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8
Signer #1 certificate SHA-1 digest: 27196e386b875e76adf700e7ea84e4c6eee33dfa
```

---

## 호환성

### ✅ 작동하는 환경
- AOSP 순정 ROM (테스트 빌드)
- Android 에뮬레이터
- AOSP 테스트 키를 사용하는 커스텀 ROM
- 원본 CarCarLauncher가 설치되어 있던 기기

### ❌ 작동하지 않는 환경
- 삼성, LG, 샤오미 등 제조사 ROM (자체 플랫폼 키 사용)
- LineageOS 공식 빌드 (자체 키 사용)
- 대부분의 상용 기기

### 제조사 ROM에서 시스템 권한 획득 방법
1. **Root + Magisk 모듈**: `/system/priv-app/`에 설치
2. **Shizuku**: ADB 권한으로 일부 기능 사용
3. **커스텀 ROM 빌드**: 직접 ROM에 포함

---

## 문제 해결

### 서명 후 설치 실패
```
INSTALL_FAILED_SHARED_USER_INCOMPATIBLE
```
→ 기기의 플랫폼 키와 서명 키가 다름. 해당 기기에서는 시스템 앱으로 설치 불가.

### 권한 거부
시스템 권한이 작동하지 않으면 기기가 AOSP 테스트 키를 사용하지 않는 것.
`sharedUserId`를 제거하고 일반 앱으로 사용:

```xml
<!-- AndroidManifest.xml에서 제거 -->
android:sharedUserId="android.uid.system"
```

---

## 자동화 스크립트

### build_and_sign.ps1
```powershell
#!/usr/bin/env pwsh
$ErrorActionPreference = "Stop"

$projectDir = "D:\nMirror\carcar_launcher"
$keysDir = "D:\nMirror\tools\aosp_keys"
$apksigner = "C:\Users\jomin\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat"

Write-Host "🔨 Building APK..." -ForegroundColor Cyan
Set-Location $projectDir
& C:\flutter\bin\flutter build apk --release

Write-Host "🔐 Signing with platform key..." -ForegroundColor Cyan
$inputApk = "$projectDir\build\app\outputs\flutter-apk\app-release.apk"
$outputApk = "$projectDir\CarrotPlay-system-signed.apk"

& $apksigner sign `
  --ks "$keysDir\platform.p12" `
  --ks-key-alias platform `
  --ks-pass pass:android `
  --key-pass pass:android `
  --out $outputApk `
  $inputApk

Write-Host "✅ Done! Output: $outputApk" -ForegroundColor Green
Write-Host ""

# 검증
Write-Host "🔍 Verifying signature..." -ForegroundColor Cyan
& $apksigner verify --print-certs $outputApk
```

---

## 참고 자료

- [AOSP Platform Security Keys](https://android.googlesource.com/platform/build/+/refs/heads/master/target/product/security/)
- [Android App Signing](https://developer.android.com/studio/publish/app-signing)
- [SharedUserId 문서](https://developer.android.com/guide/topics/manifest/manifest-element#uid)
