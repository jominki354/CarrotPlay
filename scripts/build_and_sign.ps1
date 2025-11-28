#!/usr/bin/env pwsh
<#
.SYNOPSIS
    CarrotPlay APK 빌드 및 AOSP 플랫폼 키 서명 스크립트

.DESCRIPTION
    Flutter 앱을 빌드하고 AOSP 테스트 플랫폼 키로 서명합니다.
    시스템 앱 권한(INJECT_EVENTS, ADD_TRUSTED_DISPLAY 등)을 사용하려면
    이 키로 서명된 APK를 AOSP 테스트 키 기반 기기에 설치해야 합니다.

.EXAMPLE
    .\build_and_sign.ps1
    
.EXAMPLE
    .\build_and_sign.ps1 -SkipBuild
    
.EXAMPLE
    .\build_and_sign.ps1 -Install
#>

param(
    [switch]$SkipBuild,      # 빌드 건너뛰기 (이미 빌드된 APK 사용)
    [switch]$Install,        # 서명 후 ADB로 설치
    [switch]$Verbose         # 상세 출력
)

$ErrorActionPreference = "Stop"

# ============================================
# 설정
# ============================================
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectDir = Split-Path -Parent $scriptDir
$keysDir = Join-Path (Split-Path -Parent $projectDir) "tools\aosp_keys"

# Flutter 경로 (환경에 맞게 수정)
$flutter = "C:\flutter\bin\flutter"
if (-not (Test-Path $flutter)) {
    $flutter = "flutter"  # PATH에서 찾기
}

# Android SDK apksigner 경로
$sdkHome = $env:ANDROID_HOME
if (-not $sdkHome) {
    $sdkHome = "$env:LOCALAPPDATA\Android\Sdk"
}

# 최신 build-tools 찾기
$buildToolsDir = Get-ChildItem "$sdkHome\build-tools" -Directory | 
    Sort-Object Name -Descending | 
    Select-Object -First 1
$apksigner = Join-Path $buildToolsDir.FullName "apksigner.bat"

# 파일 경로
$inputApk = Join-Path $projectDir "build\app\outputs\flutter-apk\app-release.apk"
$outputApk = Join-Path $projectDir "CarrotPlay-system-signed.apk"
$keystorePath = Join-Path $keysDir "platform.p12"

# ============================================
# 함수
# ============================================
function Write-Step {
    param([string]$Message, [string]$Color = "Cyan")
    Write-Host ""
    Write-Host "▶ $Message" -ForegroundColor $Color
}

function Write-Success {
    param([string]$Message)
    Write-Host "✅ $Message" -ForegroundColor Green
}

function Write-Error2 {
    param([string]$Message)
    Write-Host "❌ $Message" -ForegroundColor Red
}

# ============================================
# 사전 검사
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Yellow
Write-Host "   CarrotPlay - AOSP Platform Key Signing Tool" -ForegroundColor Yellow
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Yellow

# 키스토어 확인
if (-not (Test-Path $keystorePath)) {
    Write-Error2 "키스토어를 찾을 수 없습니다: $keystorePath"
    Write-Host "docs/SYSTEM_SIGNING_GUIDE.md를 참고하여 키를 생성하세요." -ForegroundColor Yellow
    exit 1
}

# apksigner 확인
if (-not (Test-Path $apksigner)) {
    Write-Error2 "apksigner를 찾을 수 없습니다: $apksigner"
    Write-Host "Android SDK Build Tools가 설치되어 있는지 확인하세요." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "프로젝트: $projectDir" -ForegroundColor DarkGray
Write-Host "키스토어: $keystorePath" -ForegroundColor DarkGray
Write-Host "apksigner: $apksigner" -ForegroundColor DarkGray

# ============================================
# 빌드
# ============================================
if (-not $SkipBuild) {
    Write-Step "Flutter 릴리스 빌드 중..."
    
    Push-Location $projectDir
    try {
        & $flutter build apk --release
        if ($LASTEXITCODE -ne 0) {
            throw "Flutter 빌드 실패"
        }
    }
    finally {
        Pop-Location
    }
    
    Write-Success "빌드 완료"
} else {
    Write-Step "빌드 건너뛰기 (기존 APK 사용)" "Yellow"
}

# APK 확인
if (-not (Test-Path $inputApk)) {
    Write-Error2 "빌드된 APK를 찾을 수 없습니다: $inputApk"
    exit 1
}

$inputSize = (Get-Item $inputApk).Length / 1MB
Write-Host "입력 APK: $inputApk ($([math]::Round($inputSize, 1)) MB)" -ForegroundColor DarkGray

# ============================================
# 서명
# ============================================
Write-Step "AOSP 플랫폼 키로 서명 중..."

& $apksigner sign `
    --ks $keystorePath `
    --ks-key-alias platform `
    --ks-pass pass:android `
    --key-pass pass:android `
    --out $outputApk `
    $inputApk

if ($LASTEXITCODE -ne 0) {
    Write-Error2 "서명 실패"
    exit 1
}

$outputSize = (Get-Item $outputApk).Length / 1MB
Write-Success "서명 완료: $outputApk ($([math]::Round($outputSize, 1)) MB)"

# ============================================
# 검증
# ============================================
Write-Step "서명 검증 중..."

$verifyOutput = & $apksigner verify --print-certs $outputApk 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "서명 검증 완료"
    
    # SHA-1 추출
    $sha1Match = $verifyOutput | Select-String "SHA-1 digest: (.+)"
    if ($sha1Match) {
        $sha1 = $sha1Match.Matches[0].Groups[1].Value
        Write-Host "SHA-1: $sha1" -ForegroundColor DarkGray
        
        # 예상 SHA-1과 비교
        $expectedSha1 = "27196e386b875e76adf700e7ea84e4c6eee33dfa"
        if ($sha1 -eq $expectedSha1) {
            Write-Success "AOSP 테스트 키 서명 확인됨 ✓"
        } else {
            Write-Host "⚠️ 서명 키가 예상과 다릅니다" -ForegroundColor Yellow
        }
    }
} else {
    Write-Error2 "서명 검증 실패"
    Write-Host $verifyOutput -ForegroundColor Red
}

# ============================================
# 설치 (옵션)
# ============================================
if ($Install) {
    Write-Step "ADB로 설치 중..."
    
    $adb = Join-Path $sdkHome "platform-tools\adb.exe"
    if (-not (Test-Path $adb)) {
        $adb = "adb"
    }
    
    # 기존 앱 제거 (충돌 방지)
    & $adb uninstall com.example.carcar_launcher 2>$null
    
    & $adb install $outputApk
    if ($LASTEXITCODE -eq 0) {
        Write-Success "설치 완료!"
    } else {
        Write-Error2 "설치 실패. 기기가 AOSP 테스트 키를 사용하지 않을 수 있습니다."
    }
}

# ============================================
# 완료
# ============================================
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "   완료!" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "출력 파일: $outputApk" -ForegroundColor White
Write-Host ""
Write-Host "설치 명령어:" -ForegroundColor Cyan
Write-Host "  adb install `"$outputApk`"" -ForegroundColor White
Write-Host ""
Write-Host "⚠️ 이 APK는 AOSP 테스트 키 기반 기기에서만 시스템 권한을 얻습니다." -ForegroundColor Yellow
Write-Host ""
