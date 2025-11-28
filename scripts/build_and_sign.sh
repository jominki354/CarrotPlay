#!/bin/bash
#
# CarrotPlay - AOSP Platform Key Signing Script
# 
# Usage:
#   ./build_and_sign.sh          # 빌드 + 서명
#   ./build_and_sign.sh --skip   # 빌드 건너뛰기
#   ./build_and_sign.sh --install # 서명 후 설치
#

set -e

# ============================================
# 설정
# ============================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
KEYS_DIR="$(dirname "$PROJECT_DIR")/tools/aosp_keys"

# Flutter
FLUTTER="${FLUTTER:-flutter}"

# Android SDK
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [[ "$OSTYPE" == "darwin"* ]]; then
    ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
fi

# 최신 build-tools 찾기
BUILD_TOOLS_DIR=$(ls -d "$ANDROID_HOME/build-tools"/*/ 2>/dev/null | sort -V | tail -1)
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"

# 파일 경로
INPUT_APK="$PROJECT_DIR/build/app/outputs/flutter-apk/app-release.apk"
OUTPUT_APK="$PROJECT_DIR/CarrotPlay-system-signed.apk"
KEYSTORE="$KEYS_DIR/platform.p12"

# 옵션
SKIP_BUILD=false
INSTALL=false

# ============================================
# 옵션 파싱
# ============================================
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip|-s)
            SKIP_BUILD=true
            shift
            ;;
        --install|-i)
            INSTALL=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# ============================================
# 함수
# ============================================
print_step() {
    echo ""
    echo -e "\033[36m▶ $1\033[0m"
}

print_success() {
    echo -e "\033[32m✅ $1\033[0m"
}

print_error() {
    echo -e "\033[31m❌ $1\033[0m"
}

# ============================================
# 사전 검사
# ============================================
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "   CarrotPlay - AOSP Platform Key Signing Tool"
echo "═══════════════════════════════════════════════════════════════"

if [[ ! -f "$KEYSTORE" ]]; then
    print_error "키스토어를 찾을 수 없습니다: $KEYSTORE"
    echo "docs/SYSTEM_SIGNING_GUIDE.md를 참고하여 키를 생성하세요."
    exit 1
fi

if [[ ! -f "$APKSIGNER" ]]; then
    print_error "apksigner를 찾을 수 없습니다: $APKSIGNER"
    echo "Android SDK Build Tools가 설치되어 있는지 확인하세요."
    exit 1
fi

echo ""
echo "프로젝트: $PROJECT_DIR"
echo "키스토어: $KEYSTORE"
echo "apksigner: $APKSIGNER"

# ============================================
# 빌드
# ============================================
if [[ "$SKIP_BUILD" == false ]]; then
    print_step "Flutter 릴리스 빌드 중..."
    
    pushd "$PROJECT_DIR" > /dev/null
    $FLUTTER build apk --release
    popd > /dev/null
    
    print_success "빌드 완료"
else
    print_step "빌드 건너뛰기 (기존 APK 사용)"
fi

if [[ ! -f "$INPUT_APK" ]]; then
    print_error "빌드된 APK를 찾을 수 없습니다: $INPUT_APK"
    exit 1
fi

INPUT_SIZE=$(du -h "$INPUT_APK" | cut -f1)
echo "입력 APK: $INPUT_APK ($INPUT_SIZE)"

# ============================================
# 서명
# ============================================
print_step "AOSP 플랫폼 키로 서명 중..."

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias platform \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUTPUT_APK" \
    "$INPUT_APK"

OUTPUT_SIZE=$(du -h "$OUTPUT_APK" | cut -f1)
print_success "서명 완료: $OUTPUT_APK ($OUTPUT_SIZE)"

# ============================================
# 검증
# ============================================
print_step "서명 검증 중..."

if "$APKSIGNER" verify --print-certs "$OUTPUT_APK"; then
    print_success "서명 검증 완료"
    
    SHA1=$("$APKSIGNER" verify --print-certs "$OUTPUT_APK" | grep "SHA-1 digest" | head -1 | awk '{print $NF}')
    EXPECTED_SHA1="27196e386b875e76adf700e7ea84e4c6eee33dfa"
    
    if [[ "$SHA1" == "$EXPECTED_SHA1" ]]; then
        print_success "AOSP 테스트 키 서명 확인됨 ✓"
    else
        echo "⚠️ 서명 키가 예상과 다릅니다"
    fi
else
    print_error "서명 검증 실패"
fi

# ============================================
# 설치 (옵션)
# ============================================
if [[ "$INSTALL" == true ]]; then
    print_step "ADB로 설치 중..."
    
    ADB="$ANDROID_HOME/platform-tools/adb"
    if [[ ! -f "$ADB" ]]; then
        ADB="adb"
    fi
    
    $ADB uninstall com.example.carcar_launcher 2>/dev/null || true
    
    if $ADB install "$OUTPUT_APK"; then
        print_success "설치 완료!"
    else
        print_error "설치 실패. 기기가 AOSP 테스트 키를 사용하지 않을 수 있습니다."
    fi
fi

# ============================================
# 완료
# ============================================
echo ""
echo "═══════════════════════════════════════════════════════════════"
echo "   완료!"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "출력 파일: $OUTPUT_APK"
echo ""
echo "설치 명령어:"
echo "  adb install \"$OUTPUT_APK\""
echo ""
echo "⚠️ 이 APK는 AOSP 테스트 키 기반 기기에서만 시스템 권한을 얻습니다."
echo ""
