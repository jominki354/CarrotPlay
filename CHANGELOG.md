# Changelog

All notable changes to this project will be documented in this file.

## [0.2.1] - 2025-11-29

### Fixed
- PIP 하단 제스쳐바 상태 초기화 문제 수정
- 제스쳐바 드래그 후 복귀 지연 현상 해결
- 프리셋 버튼 오버플로우 해결 (Column → Stack → Column 구조 재정리)
- BouncyButton 터치 인식 개선 (최소 터치 영역 44x44, 반응 속도 향상)
- Dock 레이아웃 반응형 기준값 조정 (SafeArea 적용 후 실제 높이 기준)
- 캐리어명 표시 조건 수정 (항상 표시)
- PIP 앱 종료 방지 로직 추가 (canGoBack 확인)

## [0.2.0] - 2025-11-28

### Added
- 분할 PIP 시스템: 좌우 2개 VirtualDisplay 동시 실행
- 비율 조절 슬라이더: 30:70 ~ 70:30 (5% 단위 스냅)
- 1초 롱프레스 후 드래그 활성화 (실수 방지)
- 프리셋 시스템: 3개 프리셋 슬롯 (비율, 앱, 스케일 저장)
- 프리셋 편집 다이얼로그: 비율 선택, PIP 앱 설정
- 앱 서랍 개선: 그리드 레이아웃 (2행 5열), 페이지네이션
- 앱 서랍 제스처: 상단 바 드래그로 슬라이드 닫기
- 앱 서랍 자동 닫기: 프리셋/앱 선택 시
- VirtualDisplay 해상도 개선: devicePixelRatio 기반 실제 픽셀 렌더링
- 앱 아이콘 로딩: Native에서 Bitmap → PNG 변환
- 햅틱 피드백: 비율 조절 활성화 시

## [0.1.0] - 2025-11-27

### Added
- 초기 프로젝트 설정
- VirtualDisplay 생성 및 텍스처 렌더링
- InputManager 기반 터치 주입
- MethodChannel Native 브릿지
- 시스템 앱 권한 (android.uid.system)
- AOSP 플랫폼 키 서명 지원
