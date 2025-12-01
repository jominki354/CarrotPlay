import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/rendering.dart';
import 'native_service.dart';
import 'app_selection_screen.dart';
import 'app_drawer_content.dart';
import 'home_screen.dart' show kUseNativePip;  // Native PIP 모드 플래그
import 'package:get/get.dart';
import 'dart:async';
import 'dart:math' as math;

/// PlatformView 사용 여부 플래그
/// true: 원본 앱 방식 (Native에서 터치 처리, 100% 정확)
/// false: 기존 방식 (Flutter Texture + Listener)
/// 
/// PlatformView 방식의 핵심:
/// - AndroidView에서 gestureRecognizers를 설정하여 Flutter가 터치를 가로채지 않음
/// - Native SurfaceView(AppViewSurface)에서 터치 직접 처리
/// - 원본 MotionEvent의 deviceId, downTime, source 등 모든 속성 유지
const bool kUsePlatformView = true;

class PipView extends StatefulWidget {
  final int displayId; // Logical ID for our app (1 or 2, 0 for fullscreen)
  final String label;
  final bool isFullscreen; // 전체화면 모드 여부
  final VoidCallback? onSelectApp; // 앱 선택 버튼 클릭 시 콜백

  const PipView({
    super.key,
    required this.displayId,
    required this.label,
    this.isFullscreen = false,
    this.onSelectApp,
  });

  @override
  State<PipView> createState() => PipViewState();
}

class PipViewState extends State<PipView> with SingleTickerProviderStateMixin {
  int? _textureId;
  int? _virtualDisplayId;
  int? _platformViewId; // PlatformView 모드에서 사용
  String? _currentPackage;
  String? _currentAppName;
  bool _isInitializing = false;
  String? _errorMessage;
  bool _initialized = false;
  bool _isLaunching = false; // 앱 실행 중 상태 (topActivity 체크 무시용)
  
  // PIP 내 앱 서랍 표시 모드
  bool _showInlinePipDrawer = false;
  
  // 앱 서랍 슬라이드 애니메이션
  double _drawerSlideOffset = 0.0; // 0 = 완전히 보임, 1 = 완전히 숨김
  bool _isDraggingDrawer = false;
  
  /// 외부에서 VirtualDisplay ID에 접근할 수 있는 getter
  int? get virtualDisplayId => _virtualDisplayId;
  
  // 터치 카운터 (성능 측정용)
  int _touchDownCount = 0;
  int _touchMoveCount = 0;
  int _touchUpCount = 0;
  
  /// 터치 카운터 getter
  int get touchDownCount => _touchDownCount;
  int get touchMoveCount => _touchMoveCount;
  int get touchUpCount => _touchUpCount;
  
  /// 터치 카운터 리셋
  void resetTouchCounters() {
    _touchDownCount = 0;
    _touchMoveCount = 0;
    _touchUpCount = 0;
  }
  
  /// 앱 종료 시 호출 - 투명 상태로 복귀
  void clearApp() {
    setState(() {
      _currentPackage = null;
      _currentAppName = null;
      _showInlinePipDrawer = false;
    });
  }
  
  /// 현재 실행 중인 앱 패키지 이름
  String? get currentPackage => _currentPackage;
  
  /// 외부에서 PIP 내 앱서랍 열기 (아래에서 위로 올라오는 애니메이션)
  void openInlineDrawer() {
    setState(() {
      _showInlinePipDrawer = true;
      _drawerSlideOffset = 1.0; // 화면 아래에서 시작
    });
    // 다음 프레임에서 애니메이션 시작
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _animateDrawerOpen();
      }
    });
  }
  
  /// 외부에서 PIP 내 앱서랍 닫기
  void closeInlineDrawer() {
    if (_showInlinePipDrawer) {
      _animateDrawerClose();
    }
  }
  
  /// 외부에서 뒤로가기 키 전송 (앱 종료 방지)
  Future<void> sendBackKey() async {
    if (_virtualDisplayId == null) return;
    
    // 뒤로가기 가능 여부 확인 (앱이 종료될 수 있는지)
    final canGoBack = await NativeService.canGoBack(_virtualDisplayId!);
    
    if (canGoBack) {
      // 뒤로가기 가능하면 전송
      NativeService.sendBackKey(_virtualDisplayId!);
    } else {
      // 뒤로가기 불가 (앱 종료 방지) - 진동 피드백만
      HapticFeedback.lightImpact();
      debugPrint('PIP: Back blocked - app would close');
    }
  }
  
  /// 외부에서 VirtualDisplay 크기 재조정 (비율 변경 시)
  /// totalWidth는 이미 Dock이 제외된 콘텐츠 영역 너비
  Future<void> resizeToFit(double widthRatio, double totalWidth, double totalHeight, double devicePixelRatio) async {
    if (_virtualDisplayId == null || !_initialized) return;
    
    // 새 크기 계산 (totalWidth는 이미 Dock 제외됨, 구분선 8px만 추가 제거)
    final pipWidth = (totalWidth - 8) * widthRatio - 8; // 구분선 + 마진 고려
    final pipHeight = totalHeight - 24 - 8; // 하단 제스처바 + 마진
    
    final newWidth = (pipWidth * devicePixelRatio).toInt();
    final newHeight = (pipHeight * devicePixelRatio).toInt();
    
    print("resizeToFit: widthRatio=$widthRatio, totalWidth=$totalWidth -> pipWidth=$pipWidth, newWidth=$newWidth");
    
    if (newWidth <= 0 || newHeight <= 0) {
      print("resizeToFit: invalid size, skipping");
      return;
    }
    
    // 이미 같은 크기면 skip (중복 호출 방지)
    if (newWidth == _virtualDisplayWidth && newHeight == _virtualDisplayHeight) {
      print("resizeToFit: same size, skipping");
      return;
    }
    
    final targetDpi = (160 * devicePixelRatio).toInt();
    
    debugPrint('PipView resizeToFit: ${newWidth}x$newHeight @ ${targetDpi}dpi');
    
    final success = await NativeService.resizeVirtualDisplay(
      _virtualDisplayId!,
      newWidth,
      newHeight,
      targetDpi,
    );
    
    if (success && mounted) {
      setState(() {
        _virtualDisplayWidth = newWidth;
        _virtualDisplayHeight = newHeight;
        _currentDisplayWidth = newWidth;
        _currentDisplayHeight = newHeight;
      });
    }
  }
  
  /// 비동기 크기 재조정 (결과 대기 없음 - 프리셋 전환 최적화용)
  void resizeToFitAsync(double widthRatio, double totalWidth, double totalHeight, double devicePixelRatio) {
    if (_virtualDisplayId == null || !_initialized) return;
    
    final pipWidth = (totalWidth - 8) * widthRatio - 8;
    final pipHeight = totalHeight - 24 - 8;
    
    final newWidth = (pipWidth * devicePixelRatio).toInt();
    final newHeight = (pipHeight * devicePixelRatio).toInt();
    
    if (newWidth <= 0 || newHeight <= 0) return;
    if (newWidth == _virtualDisplayWidth && newHeight == _virtualDisplayHeight) return;
    
    final targetDpi = (160 * devicePixelRatio).toInt();
    
    // UI 상태 먼저 업데이트 (즉시)
    if (mounted) {
      setState(() {
        _virtualDisplayWidth = newWidth;
        _virtualDisplayHeight = newHeight;
        _currentDisplayWidth = newWidth;
        _currentDisplayHeight = newHeight;
      });
    }
    
    // Native resize는 백그라운드에서 (결과 대기 안 함)
    NativeService.resizeVirtualDisplay(_virtualDisplayId!, newWidth, newHeight, targetDpi);
  }
  
  // 터치 관련
  bool _showControls = false;
  Timer? _controlsTimer;
  int _virtualDisplayWidth = 0;  // 기본 크기 (스케일 100% 기준)
  int _virtualDisplayHeight = 0; // 기본 크기 (스케일 100% 기준)
  int _currentDisplayWidth = 0;  // 현재 실제 크기 (스케일 적용 후)
  int _currentDisplayHeight = 0; // 현재 실제 크기 (스케일 적용 후)
  
  // 실시간 터치 추적 (원본 앱 방식)
  bool _isPointerDown = false;
  Offset? _currentPointerPosition; // 현재 터치 위치
  
  // 터치 포인터 시각화용 - 꼬리 궤적
  final List<Offset> _pointerTrail = [];
  static const int _maxTrailLength = 15;
  
  // 성능 최적화: RenderBox 캐싱, MOVE throttle
  Size? _cachedViewSize;
  int _lastMoveTime = 0;
  static const int _moveThrottleMs = 8; // ~120fps (고반응성)
  
  // 탭 감지용 (Phase 2)
  Offset? _downPosition; // DOWN 시점 위치
  static const double _tapThreshold = 15.0; // 탭으로 인식할 최대 이동 거리
  
  // 원본 PointerEvent 속성 저장 (원본 앱 방식 - UP에서 사용)
  int _lastDevice = 0;
  double _lastPressure = 1.0;
  double _lastSize = 1.0;
  
  // PIP 크기/DPI 조절 (원본 앱 방식)
  double _scaleFactor = 1.0; // 0.5 ~ 1.5
  static const double _minScale = 0.5;
  static const double _maxScale = 1.5;
  static const double _scaleStep = 0.05; // 5% 단위
  
  // 앱 종료 감지 타이머
  Timer? _appCheckTimer;

  @override
  void initState() {
    super.initState();
    // Delay initialization to ensure layout is complete
    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) {
        _initializeVirtualDisplay();
      }
    });
    
    // 앱 종료 감지 타이머 (Flutter PIP 모드 + 전체화면이 아닐 때만)
    // Native PIP 모드에서는 Native가 직접 관리하므로 불필요한 MethodChannel 호출 방지
    if (!widget.isFullscreen && !kUseNativePip) {
      _appCheckTimer = Timer.periodic(const Duration(milliseconds: 500), (_) => _checkAppRunning());
    }
  }
  
  /// 앱이 여전히 실행 중인지 확인
  /// topActivity가 없으면 투명으로, 다른 앱이면 그 앱으로 업데이트
  Future<void> _checkAppRunning() async {
    // 앱 실행 중이거나 현재 패키지가 없으면 무시
    if (_virtualDisplayId == null || _currentPackage == null || _isLaunching) return;
    
    final topActivity = await NativeService.getTopActivity(_virtualDisplayId!);
    
    // topActivity가 null이거나 비어있으면 앱 종료됨 -> 투명으로
    if (topActivity == null || topActivity.isEmpty) {
      if (mounted) {
        setState(() {
          _currentPackage = null;
          _currentAppName = null;
        });
      }
      return;
    }
    
    // topActivity가 현재 앱과 다르면 -> 그 앱으로 업데이트 (forceStop 안 함)
    if (!topActivity.startsWith(_currentPackage!)) {
      final actualPackage = topActivity.split('/').first;
      if (mounted && actualPackage.isNotEmpty) {
        setState(() {
          _currentPackage = actualPackage;
          _currentAppName = actualPackage.split('.').last;
        });
      }
    }
  }

  Future<void> _initializeVirtualDisplay() async {
    if (_isInitializing || _initialized) return;
    
    setState(() {
      _isInitializing = true;
      _errorMessage = null;
    });

    try {
      // 화면 크기 기반 계산 (원본 앱 방식)
      final mediaQuery = MediaQuery.of(context);
      final screenSize = mediaQuery.size;
      
      // 가로모드 확인 - 아직 세로면 대기
      if (mediaQuery.orientation != Orientation.landscape) {
        print("Still in portrait mode, waiting for landscape...");
        setState(() {
          _isInitializing = false;
        });
        Future.delayed(const Duration(milliseconds: 300), () {
          if (mounted && !_initialized) {
            _initializeVirtualDisplay();
          }
        });
        return;
      }
      
      // 화면 전체 크기에서 PIP 영역 계산
      final totalWidth = screenSize.width;
      final totalHeight = screenSize.height;
      final devicePixelRatio = mediaQuery.devicePixelRatio;
      
      int width, height;
      
      if (widget.isFullscreen) {
        // 전체화면 모드: Dock(72px) 제외한 전체 영역
        // 실제 픽셀 해상도로 계산 (자글자글 방지)
        width = ((totalWidth - 72) * devicePixelRatio).toInt();
        height = (totalHeight * devicePixelRatio).toInt();
        print("Fullscreen mode: ${width}x$height (pixelRatio: $devicePixelRatio)");
      } else {
        // 분할 모드: 각 PIP 영역 = (전체 너비 - 네비 72px - 구분선 12px) / 2 - 마진
        // 실제 픽셀 해상도로 계산
        width = (((totalWidth - 72 - 12) / 2 - 8) * devicePixelRatio).toInt();
        height = ((totalHeight - 8) * devicePixelRatio).toInt();
      }
      
      // 안전장치: 여전히 세로 비율이면 swap
      if (height > width * 2) {
        print("Detected portrait ratio, swapping dimensions");
        final temp = width;
        width = height;
        height = temp;
      }

      if (width <= 0 || height <= 0) {
        print("Invalid dimensions detected: ${width}x$height. Retrying in 500ms...");
        setState(() {
          _isInitializing = false;
        });
        Future.delayed(const Duration(milliseconds: 500), () {
          if (mounted && !_initialized) {
            _initializeVirtualDisplay();
          }
        });
        return;
      }

      print("Creating VirtualDisplay for ${widget.label}: ${width}x$height (screen: ${totalWidth}x${totalHeight})");
      
      // DPI는 devicePixelRatio 기반으로 설정 (선명한 화질)
      final targetDpi = (160 * devicePixelRatio).toInt();
      
      print("Target DPI: $targetDpi (devicePixelRatio: $devicePixelRatio)");
      
      // PlatformView 모드: 크기만 저장하고 AndroidView가 VirtualDisplay 생성
      if (kUsePlatformView) {
        setState(() {
          _virtualDisplayWidth = width;
          _virtualDisplayHeight = height;
          _currentDisplayWidth = width;
          _currentDisplayHeight = height;
          _isInitializing = false;
          // _initialized는 PlatformView에서 displayId를 받으면 true로 설정
        });
        print("PlatformView mode: waiting for AndroidView to create display");
        return;
      }
      
      // 기존 Texture 모드
      final result = await NativeService.createVirtualDisplay(width, height, targetDpi);
      
      if (result == null) {
        throw Exception("VirtualDisplay creation returned null");
      }

      setState(() {
        _textureId = result['textureId'];
        _virtualDisplayId = result['displayId'];
        _virtualDisplayWidth = width;   // 기본 크기 저장
        _virtualDisplayHeight = height; // 기본 크기 저장
        _currentDisplayWidth = width;   // 현재 크기 = 기본 크기
        _currentDisplayHeight = height; // 현재 크기 = 기본 크기
        _initialized = true;
        _isInitializing = false;
      });
      
      print("VirtualDisplay created: texture=$_textureId, display=$_virtualDisplayId");
    } catch (e) {
      print("Error initializing VirtualDisplay: $e");
      setState(() {
        _errorMessage = e.toString();
        _isInitializing = false;
      });
    }
  }

  Future<void> _selectApp() async {
    // PlatformView 모드에서는 displayId가 없을 수 있음 (아직 생성 전)
    if (!kUsePlatformView && _virtualDisplayId == null) {
      print("VirtualDisplay not ready");
      return;
    }

    try {
      final result = await Get.to(() => const AppSelectionScreen());
      if (result != null && result is Map) {
        final packageName = result['packageName'] as String?;
        final appName = result['appName'] as String?;
        
        if (packageName != null) {
          print("Launching $packageName on display $_virtualDisplayId (platformViewId=$_platformViewId)");
          
          setState(() {
            _currentPackage = packageName;
            _currentAppName = appName ?? packageName;
          });
          
          bool success = false;
          
          // PlatformView 모드
          if (kUsePlatformView && _platformViewId != null) {
            success = await _launchAppViaPlatformView(packageName);
          } else if (_virtualDisplayId != null) {
            // 기존 Texture 모드
            success = await NativeService.launchApp(packageName, _virtualDisplayId!);
          }
          
          if (!success) {
            print("Failed to launch $appName");
          } else {
            print("Launched $appName");
          }
        }
      }
    } catch (e) {
      print("Error selecting/launching app: $e");
    }
  }
  
  /// PlatformView를 통해 앱 실행
  Future<bool> _launchAppViaPlatformView(String packageName) async {
    if (_platformViewId == null) return false;
    
    const channel = MethodChannel('android.test.settings/virtual_display_view_channel');
    try {
      final result = await channel.invokeMethod<bool>('launchApp', {
        'viewId': _platformViewId,
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      print("Failed to launch app via PlatformView: $e");
      return false;
    }
  }

  /// 프리셋에서 앱 실행 (외부 호출용)
  /// VirtualDisplay가 준비될 때까지 대기 후 실행
  Future<void> launchAppWithConfig(String packageName, {double scale = 1.0}) async {
    // PlatformView 모드: displayId가 준비될 때까지 대기
    int waitCount = 0;
    if (kUsePlatformView) {
      while (_virtualDisplayId == null && waitCount < 50) {
        print("Waiting for PlatformView displayId... ($waitCount)");
        await Future.delayed(const Duration(milliseconds: 100));
        waitCount++;
      }
    } else {
      // 기존 Texture 모드
      while (_virtualDisplayId == null && waitCount < 30) {
        print("Waiting for VirtualDisplay... ($waitCount)");
        await Future.delayed(const Duration(milliseconds: 100));
        waitCount++;
      }
    }
    
    if (_virtualDisplayId == null && !kUsePlatformView) {
      print("VirtualDisplay not ready for $packageName after waiting");
      return;
    }

    try {
      // 앱 실행 중 상태 설정 (_checkAppRunning 무시)
      _isLaunching = true;
      
      print("Launching $packageName on display $_virtualDisplayId (platformView=$kUsePlatformView) with scale $scale");
      
      // 스케일 적용 - DPI도 함께 조정 (변경된 경우에만)
      final targetScale = scale.clamp(_minScale, _maxScale);
      if ((targetScale - _scaleFactor).abs() > 0.01) {
        await _applyScaleValue(targetScale);
      }
      
      setState(() {
        _currentPackage = packageName;
        _currentAppName = packageName.split('.').last;
      });
      
      bool success = false;
      
      // PlatformView 모드
      if (kUsePlatformView && _platformViewId != null) {
        success = await _launchAppViaPlatformView(packageName);
      } else if (_virtualDisplayId != null) {
        // 기존 Texture 모드
        success = await NativeService.launchApp(packageName, _virtualDisplayId!);
      }
      
      if (!success) {
        print("Failed to launch $packageName");
      } else {
        print("Successfully launched $packageName");
      }
      
      // 앱이 실행될 시간을 주고 _isLaunching 해제 (1.5초 후)
      Future.delayed(const Duration(milliseconds: 1500), () {
        _isLaunching = false;
      });
    } catch (e) {
      print("Error launching app with config: $e");
      _isLaunching = false;
    }
  }
  
  /// 빠른 앱 실행 (프리셋 전환 최적화용 - 대기 없음)
  /// VirtualDisplay가 이미 준비되어 있다고 가정하고 바로 실행
  void launchAppWithConfigFast(String packageName, {double scale = 1.0}) {
    // displayId가 없으면 일반 버전으로 fallback
    if (_virtualDisplayId == null) {
      launchAppWithConfig(packageName, scale: scale);
      return;
    }
    
    // 앱 실행 중 상태 설정
    _isLaunching = true;
    
    // UI 상태 즉시 업데이트
    if (mounted) {
      setState(() {
        _currentPackage = packageName;
        _currentAppName = packageName.split('.').last;
      });
    }
    
    // 스케일이 다르면 적용 (비동기, 대기 안 함)
    final targetScale = scale.clamp(_minScale, _maxScale);
    if ((targetScale - _scaleFactor).abs() > 0.01) {
      _applyScaleValueFast(targetScale);
    }
    
    // 앱 실행 (비동기, 대기 안 함)
    if (kUsePlatformView && _platformViewId != null) {
      _launchAppViaPlatformView(packageName);
    } else {
      NativeService.launchApp(packageName, _virtualDisplayId!);
    }
    
    // 1.5초 후 _isLaunching 해제
    Future.delayed(const Duration(milliseconds: 1500), () {
      _isLaunching = false;
    });
  }
  
  /// 빠른 스케일 적용 (대기 없음)
  void _applyScaleValueFast(double newScale) {
    if (_virtualDisplayId == null || _virtualDisplayWidth == 0) return;
    
    final newWidth = (_virtualDisplayWidth * newScale).toInt();
    final newHeight = (_virtualDisplayHeight * newScale).toInt();
    final newDensity = (160 * newScale).toInt();
    
    // 상태 즉시 업데이트
    _scaleFactor = newScale;
    
    // Native 호출 (대기 안 함)
    NativeService.resizeVirtualDisplay(_virtualDisplayId!, newWidth, newHeight, newDensity);
  }

  /// 스케일 값을 적용하는 내부 메서드
  /// 스케일이 커지면 해상도가 커지고 DPI도 비례해서 증가
  Future<void> _applyScaleValue(double newScale) async {
    if (_virtualDisplayId == null || _virtualDisplayWidth == 0 || _virtualDisplayHeight == 0) return;
    
    final newWidth = (_virtualDisplayWidth * newScale).toInt();
    final newHeight = (_virtualDisplayHeight * newScale).toInt();
    
    // DPI도 스케일에 비례하여 조정 (기본 160 기준)
    // scale 1.0 = 160dpi, scale 1.5 = 240dpi, scale 2.0 = 320dpi
    final newDensity = (160 * newScale).toInt();
    
    print("Applying scale $newScale: ${newWidth}x$newHeight @ ${newDensity}dpi");
    
    final success = await NativeService.resizeVirtualDisplay(
      _virtualDisplayId!,
      newWidth,
      newHeight,
      newDensity,
    );
    
    if (success) {
      setState(() {
        _scaleFactor = newScale;
        _currentDisplayWidth = newWidth;
        _currentDisplayHeight = newHeight;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // 전체화면 모드: margin, border, 둥근모서리 없이 꽉 채움
    if (widget.isFullscreen) {
      return Container(
        color: Colors.black,
        child: _buildContent(),
      );
    }
    
    // 분할 PIP 모드: 가우시안 블러 + 테두리
    return Container(
      margin: const EdgeInsets.only(left: 4, top: 4, right: 4, bottom: 0), // 하단 여백 없음 (제스처바 영역)
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12.0),
        border: Border.all(
          color: Colors.white.withOpacity(0.15),
          width: 1,
        ),
      ),
      clipBehavior: Clip.hardEdge,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(11.0),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: Stack(
            children: [
              // 배경 + 콘텐츠
              Container(
                color: _currentPackage != null 
                    ? Colors.black 
                    : Colors.white.withOpacity(0.05),
                child: _buildContent(),
              ),
              // PIP 내 앱 서랍 오버레이
              if (_showInlinePipDrawer)
                Positioned.fill(
                  child: _buildInlinePipDrawer(),
                ),
            ],
          ),
        ),
      ),
    );
  }
  
  /// 앱 미실행 시 중앙에 표시되는 앱 선택 버튼
  Widget _buildAppSelectButton() {
    return GestureDetector(
      onTap: () {
        // PIP 내 앱 서랍 표시
        setState(() {
          _showInlinePipDrawer = true;
        });
      },
      child: Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: Colors.white.withOpacity(0.1),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: Colors.white.withOpacity(0.2),
            width: 1.5,
          ),
        ),
        child: const Icon(
          Icons.add_rounded,
          color: Colors.white54,
          size: 32,
        ),
      ),
    );
  }
  
  /// PIP 내 앱 서랍 (슬라이드 애니메이션) - 제스처에 따라 부드럽게 이동
  Widget _buildInlinePipDrawer() {
    final apps = AppCache().apps;
    
    return LayoutBuilder(
      builder: (context, constraints) {
        final drawerHeight = constraints.maxHeight;
        
        return GestureDetector(
          onVerticalDragStart: (details) {
            _isDraggingDrawer = true;
          },
          onVerticalDragUpdate: (details) {
            // 드래그에 따라 오프셋 업데이트 (아래로 드래그 = 닫기)
            setState(() {
              _drawerSlideOffset += details.delta.dy / drawerHeight;
              _drawerSlideOffset = _drawerSlideOffset.clamp(0.0, 1.0);
            });
          },
          onVerticalDragEnd: (details) {
            _isDraggingDrawer = false;
            final velocity = details.velocity.pixelsPerSecond.dy;
            
            // 빠른 스와이프 또는 50% 이상 드래그하면 닫기
            if (velocity > 300 || _drawerSlideOffset > 0.5) {
              _animateDrawerClose();
            } else {
              _animateDrawerOpen();
            }
          },
          child: AnimatedContainer(
            duration: _isDraggingDrawer ? Duration.zero : const Duration(milliseconds: 200),
            curve: Curves.easeOut,
            transform: Matrix4.translationValues(0, drawerHeight * _drawerSlideOffset, 0),
            child: Container(
              color: const Color(0xFF1A1A1A),
              child: Column(
                children: [
                  // 상단 제스처바 (드래그해서 닫기)
                  GestureDetector(
                    onTap: () => _animateDrawerClose(),
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      color: Colors.transparent,
                      child: Center(
                        child: Container(
                          width: 40,
                          height: 4,
                          decoration: BoxDecoration(
                            color: Colors.white38,
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                      ),
                    ),
                  ),
                  
                  // 앱 그리드
                  Expanded(
                    child: apps.isEmpty
                        ? const Center(child: CircularProgressIndicator(color: Colors.white38))
                        : GridView.builder(
                            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                              crossAxisCount: 4,
                              childAspectRatio: 1.0,
                              crossAxisSpacing: 12,
                              mainAxisSpacing: 12,
                            ),
                            itemCount: apps.length,
                            itemBuilder: (context, index) {
                              final app = apps[index];
                              return GestureDetector(
                                onTap: () {
                                  _animateDrawerClose();
                                  Future.delayed(const Duration(milliseconds: 150), () {
                                    launchAppWithConfig(app.packageName);
                                  });
                                },
                                child: Center(
                                  child: FractionallySizedBox(
                                    widthFactor: 0.7,
                                    heightFactor: 0.7,
                                    child: app.icon != null
                                        ? ClipRRect(
                                            borderRadius: BorderRadius.circular(10),
                                            child: Image.memory(
                                              app.icon!,
                                              fit: BoxFit.cover,
                                              gaplessPlayback: true,
                                            ),
                                          )
                                        : Container(
                                            decoration: BoxDecoration(
                                              color: Colors.white.withOpacity(0.1),
                                              borderRadius: BorderRadius.circular(10),
                                            ),
                                            child: const Icon(
                                              Icons.android,
                                              color: Colors.white54,
                                              size: 18,
                                            ),
                                          ),
                                  ),
                                ),
                              );
                            },
                          ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
  
  /// 서랍 닫기 애니메이션
  void _animateDrawerClose() {
    setState(() {
      _drawerSlideOffset = 1.0;
    });
    Future.delayed(const Duration(milliseconds: 200), () {
      if (mounted) {
        setState(() {
          _showInlinePipDrawer = false;
          _drawerSlideOffset = 0.0;
        });
      }
    });
  }
  
  /// 서랍 열기 애니메이션
  void _animateDrawerOpen() {
    setState(() {
      _drawerSlideOffset = 0.0;
    });
  }

  Widget _buildControlBar() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // 축소 버튼
          IconButton(
            icon: const Icon(Icons.remove_circle_outline, color: Colors.white, size: 20),
            onPressed: _scaleFactor > _minScale ? _decreaseScale : null,
            tooltip: "Decrease Size (${(_scaleFactor * 100).toInt()}%)",
            padding: const EdgeInsets.all(8),
            constraints: const BoxConstraints(),
          ),
          // 스케일 표시
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              "${(_scaleFactor * 100).toInt()}%",
              style: const TextStyle(color: Colors.white70, fontSize: 11),
            ),
          ),
          // 확대 버튼
          IconButton(
            icon: const Icon(Icons.add_circle_outline, color: Colors.white, size: 20),
            onPressed: _scaleFactor < _maxScale ? _increaseScale : null,
            tooltip: "Increase Size",
            padding: const EdgeInsets.all(8),
            constraints: const BoxConstraints(),
          ),
          const SizedBox(width: 8),
          Container(width: 1, height: 20, color: Colors.white24),
          const SizedBox(width: 8),
          // Back 버튼
          IconButton(
            icon: const Icon(Icons.arrow_back, color: Colors.white, size: 20),
            onPressed: () {
              if (_virtualDisplayId != null) {
                NativeService.sendBackKey(_virtualDisplayId!);
              }
            },
            tooltip: "Back",
            padding: const EdgeInsets.all(8),
            constraints: const BoxConstraints(),
          ),
          const SizedBox(width: 12),
          // Home 버튼
          IconButton(
            icon: const Icon(Icons.home, color: Colors.white, size: 20),
            onPressed: () {
              if (_virtualDisplayId != null) {
                NativeService.sendHomeKey(_virtualDisplayId!);
              }
            },
            tooltip: "Home",
            padding: const EdgeInsets.all(8),
            constraints: const BoxConstraints(),
          ),
          const SizedBox(width: 12),
          // Recent 버튼
          IconButton(
            icon: const Icon(Icons.layers, color: Colors.white, size: 20),
            onPressed: () {
              if (_virtualDisplayId != null) {
                NativeService.sendRecentKey(_virtualDisplayId!);
              }
            },
            tooltip: "Recent Apps",
            padding: const EdgeInsets.all(8),
            constraints: const BoxConstraints(),
          ),
        ],
      ),
    );
  }

  Widget _buildContent() {
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
              const SizedBox(height: 16),
              Text(
                "Error",
                style: Theme.of(context).textTheme.titleLarge?.copyWith(color: Colors.redAccent),
              ),
              const SizedBox(height: 8),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70, fontSize: 12),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () {
                  setState(() {
                    _errorMessage = null;
                    _initialized = false;
                  });
                  _initializeVirtualDisplay();
                },
                child: const Text("Retry"),
              ),
            ],
          ),
        ),
      );
    }

    if (_isInitializing) {
      // 초기화 중: 투명 배경
      return const SizedBox.expand();
    }
    
    // PlatformView 모드: AndroidView 사용 (원본 앱 방식)
    if (kUsePlatformView) {
      return _buildPlatformViewContent();
    }
    
    // 기존 Texture 모드
    if (_textureId == null) {
      return const SizedBox.expand();
    }
    
    // 앱이 실행되지 않은 경우: 중앙에 앱 선택 버튼 표시
    if (_currentPackage == null && !widget.isFullscreen) {
      return Center(
        child: _buildAppSelectButton(),
      );
    }

    // 터치 가능한 Texture 뷰 - Listener로 실시간 이벤트 처리
    // 포인터 시각화 제거로 성능 대폭 개선
    return Listener(
      behavior: HitTestBehavior.opaque,
      onPointerDown: _onPointerDown,
      onPointerMove: _onPointerMove,
      onPointerUp: _onPointerUp,
      onPointerCancel: _onPointerCancel,
      child: Texture(textureId: _textureId!),
    );
  }
  
  /// PlatformView 방식의 VirtualDisplay 콘텐츠
  /// Native에서 터치를 직접 처리하므로 100% 정확한 터치 인식
  Widget _buildPlatformViewContent() {
    // 앱이 실행되지 않은 경우: 중앙에 앱 선택 버튼 표시
    if (_currentPackage == null && !widget.isFullscreen && _virtualDisplayId != null) {
      return Stack(
        children: [
          // PlatformView 배경 (투명)
          _buildAndroidView(),
          // 앱 선택 버튼 오버레이
          Center(child: _buildAppSelectButton()),
        ],
      );
    }
    
    // PlatformView만 표시 (터치는 Native에서 처리)
    return _buildAndroidView();
  }
  
  /// AndroidView 빌드 (PlatformView)
  /// 고유한 key를 부여하여 불필요한 rebuild로 인한 재생성 방지
  /// 
  /// 핵심: gestureRecognizers를 EagerGestureRecognizer로 설정하여
  /// Flutter가 터치를 가로채지 않고 Native SurfaceView에서 직접 처리하도록 함
  Widget _buildAndroidView() {
    final creationParams = <String, dynamic>{
      'width': _currentDisplayWidth > 0 ? _currentDisplayWidth : 1080,
      'height': _currentDisplayHeight > 0 ? _currentDisplayHeight : 1920,
      'dpi': 320,
      // 슬롯 인덱스 전달: VirtualDisplay 캐싱에 사용
      // PlatformView가 재생성되어도 기존 VirtualDisplay 재사용
      'slotIndex': widget.displayId,
    };
    
    return AndroidView(
      // 중요: displayId 기반 고유 key로 rebuild 시 재생성 방지
      key: ValueKey('vd_${widget.displayId}_${widget.label}'),
      viewType: 'android.test.settings/virtual_display_view',
      creationParams: creationParams,
      creationParamsCodec: const StandardMessageCodec(),
      onPlatformViewCreated: _onPlatformViewCreated,
      // 핵심: EagerGestureRecognizer를 사용하여 모든 터치를 Native로 전달
      // 원본 앱 방식: Flutter가 터치를 가로채지 않고 Native에서 직접 처리
      gestureRecognizers: <Factory<OneSequenceGestureRecognizer>>{
        Factory<OneSequenceGestureRecognizer>(
          () => EagerGestureRecognizer(),
        ),
      },
    );
  }
  
  /// PlatformView 생성 완료 콜백
  void _onPlatformViewCreated(int viewId) async {
    print('[PipView] PlatformView created: viewId=$viewId');
    _platformViewId = viewId;
    
    // displayId 가져오기
    await _waitForPlatformViewDisplayId();
  }
  
  /// PlatformView의 displayId가 준비될 때까지 대기
  Future<void> _waitForPlatformViewDisplayId() async {
    const channel = MethodChannel('android.test.settings/virtual_display_view_channel');
    
    for (int i = 0; i < 50; i++) { // 최대 5초 대기
      try {
        final displayId = await channel.invokeMethod<int>('getDisplayId', {
          'viewId': _platformViewId,
        });
        
        if (displayId != null && displayId > 0) {
          print('[PipView] PlatformView display ready: displayId=$displayId');
          setState(() {
            _virtualDisplayId = displayId;
            _initialized = true;
          });
          return;
        }
      } catch (e) {
        print('[PipView] Waiting for PlatformView displayId... (attempt ${i + 1})');
      }
      await Future.delayed(const Duration(milliseconds: 100));
    }
    
    print('[PipView] Failed to get PlatformView displayId after 5 seconds');
    setState(() {
      _errorMessage = 'PlatformView display creation failed';
    });
  }

  // ============================================
  // Real-time Touch Handlers (원본 앱 방식)
  // ============================================
  
  /// 화면 좌표를 VirtualDisplay 좌표로 변환
  /// 원본 앱 방식: 현재 실제 크기 기준으로 변환 (스케일 적용된 크기)
  Offset _localToVirtualDisplay(Offset local, Size viewSize) {
    if (_currentDisplayWidth == 0 || _currentDisplayHeight == 0) {
      return local;
    }
    // 현재 VirtualDisplay의 실제 크기 기준으로 스케일 계산
    final scaleX = _currentDisplayWidth / viewSize.width;
    final scaleY = _currentDisplayHeight / viewSize.height;
    return Offset(local.dx * scaleX, local.dy * scaleY);
  }

  void _onPointerDown(PointerDownEvent event) {
    if (_virtualDisplayId == null) return;
    
    // RenderBox 캐싱
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    _cachedViewSize = renderBox.size;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // 탭 감지용 DOWN 위치 저장
    _downPosition = vdPos;
    _isPointerDown = true;
    
    // 원본 PointerEvent 속성 저장 (원본 앱 방식)
    _lastDevice = event.device;
    _lastPressure = event.pressure;
    _lastSize = event.size;
    
    // 디버그 로그
    print('[TOUCH] DOWN displayId=$_virtualDisplayId pos=(${vdPos.dx.toInt()}, ${vdPos.dy.toInt()}) device=${event.device}');
    
    // 컨트롤 표시 (setState 최소화)
    _showControlsTemporarily();
    
    // ACTION_DOWN 전송 - 원본 PointerEvent의 속성 전달
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      0, // ACTION_DOWN
      vdPos.dx,
      vdPos.dy,
      0, // Native에서 관리
      0,
      device: event.device,
      pressure: event.pressure > 0 ? event.pressure : 1.0,
      size: event.size > 0 ? event.size : 1.0,
    );
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (_virtualDisplayId == null || !_isPointerDown || _cachedViewSize == null) return;
    
    final now = DateTime.now().millisecondsSinceEpoch;
    
    // Throttle: 최소 간격 제한 (과도한 이벤트 방지)
    if (now - _lastMoveTime < _moveThrottleMs) return;
    _lastMoveTime = now;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // ACTION_MOVE 전송 - 원본 PointerEvent의 속성 전달
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      2, // ACTION_MOVE
      vdPos.dx,
      vdPos.dy,
      0, // Native에서 관리
      0,
      device: event.device,
      pressure: event.pressure > 0 ? event.pressure : 1.0,
      size: event.size > 0 ? event.size : 1.0,
    );
  }

  void _onPointerUp(PointerUpEvent event) {
    if (_virtualDisplayId == null || !_isPointerDown || _cachedViewSize == null) return;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // 탭 감지: DOWN과 UP 거리가 threshold 이내
    final isTap = _downPosition != null && 
        (vdPos - _downPosition!).distance < _tapThreshold;
    
    // 디버그 로그
    final distance = _downPosition != null ? (vdPos - _downPosition!).distance : 0.0;
    print('[TOUCH] UP displayId=$_virtualDisplayId pos=(${vdPos.dx.toInt()}, ${vdPos.dy.toInt()}) distance=${distance.toInt()} isTap=$isTap device=${event.device}');
    
    // ACTION_UP 전송 - 원본 PointerEvent의 속성 전달
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      1, // ACTION_UP
      vdPos.dx,
      vdPos.dy,
      0,
      0,
      device: event.device,
      pressure: _lastPressure > 0 ? _lastPressure : 1.0,
      size: _lastSize > 0 ? _lastSize : 1.0,
    );
    
    // 상태 리셋 (setState 제거 - 포인터 시각화 안 함)
    _isPointerDown = false;
    _downPosition = null;
    _cachedViewSize = null;
  }

  void _onPointerCancel(PointerCancelEvent event) {
    if (_virtualDisplayId == null || !_isPointerDown || _cachedViewSize == null) return;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // ACTION_CANCEL 전송 (fire-and-forget)
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      3, // ACTION_CANCEL
      vdPos.dx,
      vdPos.dy,
      0,
      0,
    );
    
    // 상태 리셋 (setState 제거)
    _isPointerDown = false;
    _downPosition = null;
    _cachedViewSize = null;
  }

  void _showControlsTemporarily() {
    setState(() => _showControls = true);
    _controlsTimer?.cancel();
    _controlsTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _showControls = false);
    });
  }

  // ============================================
  // PIP 크기 조절 (원본 앱 방식)
  // ============================================
  
  void _increaseScale() {
    if (_scaleFactor >= _maxScale) return;
    final newScale = (_scaleFactor + _scaleStep).clamp(_minScale, _maxScale);
    _applyScaleValue(newScale);
  }
  
  void _decreaseScale() {
    if (_scaleFactor <= _minScale) return;
    final newScale = (_scaleFactor - _scaleStep).clamp(_minScale, _maxScale);
    _applyScaleValue(newScale);
  }
  
  // _applyScaleValue는 위에서 정의됨 (launchAppWithConfig 근처)

  @override
  void dispose() {
    _controlsTimer?.cancel();
    _appCheckTimer?.cancel();
    if (_virtualDisplayId != null) {
      NativeService.releaseVirtualDisplay(_virtualDisplayId!);
    }
    super.dispose();
  }
}

/// 터치 포인터 시각화 Painter (Android 개발자 옵션 스타일)
/// 원형 + 꼬리 궤적 표시
class TouchPointerPainter extends CustomPainter {
  final Offset position;
  final List<Offset> trail;
  
  static const double _circleRadius = 12.0; // 원형 크기 절반으로 축소
  static const Color _circleColor = Color(0x88FFFFFF);
  static const Color _circleBorderColor = Color(0xFFFFFFFF);
  static const Color _trailColor = Color(0x66FFFFFF);
  
  TouchPointerPainter({
    required this.position,
    required this.trail,
  });
  
  @override
  void paint(Canvas canvas, Size size) {
    // 꼬리 궤적 그리기 (점점 투명해지는 선)
    if (trail.length > 1) {
      final trailPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4.0
        ..strokeCap = StrokeCap.round;
      
      for (int i = 1; i < trail.length; i++) {
        // 투명도: 이전 점일수록 투명
        final alpha = (i / trail.length * 100).toInt().clamp(20, 100);
        trailPaint.color = _trailColor.withAlpha(alpha);
        
        canvas.drawLine(trail[i - 1], trail[i], trailPaint);
      }
    }
    
    // 원형 포인터 그리기 (외곽선)
    final borderPaint = Paint()
      ..color = _circleBorderColor
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.0;
    
    canvas.drawCircle(position, _circleRadius, borderPaint);
    
    // 원형 포인터 그리기 (채움)
    final fillPaint = Paint()
      ..color = _circleColor
      ..style = PaintingStyle.fill;
    
    canvas.drawCircle(position, _circleRadius, fillPaint);
    
    // 중심점 표시 (작은 점)
    final centerPaint = Paint()
      ..color = _circleBorderColor
      ..style = PaintingStyle.fill;
    
    canvas.drawCircle(position, 4.0, centerPaint);
  }
  
  @override
  bool shouldRepaint(covariant TouchPointerPainter oldDelegate) {
    return position != oldDelegate.position || 
           trail.length != oldDelegate.trail.length;
  }
}
