import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart';
import 'native_service.dart';
import 'app_selection_screen.dart';
import 'package:get/get.dart';
import 'dart:async';
import 'dart:math' as math;

class PipView extends StatefulWidget {
  final int displayId; // Logical ID for our app (1 or 2)
  final String label;

  const PipView({
    super.key,
    required this.displayId,
    required this.label,
  });

  @override
  State<PipView> createState() => _PipViewState();
}

class _PipViewState extends State<PipView> {
  int? _textureId;
  int? _virtualDisplayId;
  String? _currentPackage;
  String? _currentAppName;
  bool _isInitializing = false;
  String? _errorMessage;
  bool _initialized = false;
  
  // 터치 관련
  bool _showControls = false;
  Timer? _controlsTimer;
  int _virtualDisplayWidth = 0;
  int _virtualDisplayHeight = 0;
  
  // 실시간 터치 추적 (원본 앱 방식)
  bool _isPointerDown = false;
  Offset? _currentPointerPosition; // 현재 터치 위치
  
  // 터치 포인터 시각화용 - 꼬리 궤적
  final List<Offset> _pointerTrail = [];
  static const int _maxTrailLength = 15;
  
  // 성능 최적화: RenderBox 캐싱, MOVE throttle
  Size? _cachedViewSize;
  int _lastMoveTime = 0;
  static const int _moveThrottleMs = 4; // ~250fps 제한

  @override
  void initState() {
    super.initState();
    // Delay initialization to ensure layout is complete
    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) {
        _initializeVirtualDisplay();
      }
    });
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
      // 레이아웃: [Nav 80px][PIP1 50%][PIP2 50%]
      final totalWidth = screenSize.width;
      final totalHeight = screenSize.height;
      
      // 각 PIP 영역 = (전체 너비 - 네비 80px - 구분선 1px - 마진 16px*2) / 2
      var width = ((totalWidth - 80 - 1 - 32) / 2).toInt();
      var height = (totalHeight - 16).toInt(); // 상하 마진 8px*2
      
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
      
      final result = await NativeService.createVirtualDisplay(width, height, 160);
      
      if (result == null) {
        throw Exception("VirtualDisplay creation returned null");
      }

      setState(() {
        _textureId = result['textureId'];
        _virtualDisplayId = result['displayId'];
        _virtualDisplayWidth = width;
        _virtualDisplayHeight = height;
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
    if (_virtualDisplayId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("VirtualDisplay not ready")),
      );
      return;
    }

    try {
      final result = await Get.to(() => const AppSelectionScreen());
      if (result != null && result is Map) {
        final packageName = result['packageName'] as String?;
        final appName = result['appName'] as String?;
        
        if (packageName != null) {
          print("Launching $packageName on display $_virtualDisplayId");
          
          setState(() {
            _currentPackage = packageName;
            _currentAppName = appName ?? packageName;
          });
          
          final success = await NativeService.launchApp(packageName, _virtualDisplayId!);
          
          if (!success) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text("Failed to launch $appName")),
            );
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text("Launched $appName")),
            );
          }
        }
      }
    } catch (e) {
      print("Error selecting/launching app: $e");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(8.0),
      decoration: BoxDecoration(
        color: Colors.black45,
        borderRadius: BorderRadius.circular(16.0),
        border: Border.all(color: Colors.white10),
      ),
      clipBehavior: Clip.hardEdge,
      child: Stack(
        children: [
          // Texture Layer or Error/Loading
          Positioned.fill(
            child: _buildContent(),
          ),

          // Overlay Controls (항상 표시되는 앱 선택 버튼)
          Positioned(
            bottom: 16,
            right: 16,
            child: FloatingActionButton.small(
              onPressed: _selectApp,
              tooltip: "Select App",
              child: const Icon(Icons.apps),
            ),
          ),
          
          // App Control Bar (터치 시 표시) - 중앙 정렬
          if (_showControls && _currentPackage != null)
            Positioned(
              bottom: 16,
              left: 0,
              right: 0,
              child: Center(child: _buildControlBar()),
            ),
          
          // Info Label
          Positioned(
            top: 16,
            left: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    widget.label,
                    style: const TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                  if (_currentAppName != null)
                    Text(
                      _currentAppName!,
                      style: const TextStyle(color: Colors.greenAccent, fontSize: 10),
                    ),
                  if (_virtualDisplayId != null)
                    Text(
                      "Display ID: $_virtualDisplayId",
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
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

    if (_isInitializing || _textureId == null) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text("Initializing VirtualDisplay...", style: TextStyle(color: Colors.white70)),
          ],
        ),
      );
    }

    // 터치 가능한 Texture 뷰 - Listener로 실시간 이벤트 처리 (원본 앱 방식)
    return Stack(
      children: [
        // Texture 뷰
        Positioned.fill(
          child: Listener(
            behavior: HitTestBehavior.opaque,
            onPointerDown: _onPointerDown,
            onPointerMove: _onPointerMove,
            onPointerUp: _onPointerUp,
            onPointerCancel: _onPointerCancel,
            child: Texture(textureId: _textureId!),
          ),
        ),
        
        // 터치 포인터 시각화 오버레이
        if (_isPointerDown && _currentPointerPosition != null)
          Positioned.fill(
            child: IgnorePointer(
              child: CustomPaint(
                painter: TouchPointerPainter(
                  position: _currentPointerPosition!,
                  trail: List.from(_pointerTrail),
                ),
              ),
            ),
          ),
      ],
    );
  }

  // ============================================
  // Real-time Touch Handlers (원본 앱 방식)
  // ============================================
  
  Offset _localToVirtualDisplay(Offset local, Size viewSize) {
    if (_virtualDisplayWidth == 0 || _virtualDisplayHeight == 0) {
      return local;
    }
    final scaleX = _virtualDisplayWidth / viewSize.width;
    final scaleY = _virtualDisplayHeight / viewSize.height;
    return Offset(local.dx * scaleX, local.dy * scaleY);
  }

  void _onPointerDown(PointerDownEvent event) {
    if (_virtualDisplayId == null) return;
    
    // RenderBox 캐싱
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    _cachedViewSize = renderBox.size;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    setState(() {
      _isPointerDown = true;
      _currentPointerPosition = event.localPosition;
      _pointerTrail.clear();
      _pointerTrail.add(event.localPosition);
    });
    
    // 컨트롤 표시
    _showControlsTemporarily();
    
    // ACTION_DOWN 즉시 전송 (fire-and-forget, await 안 함)
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      0, // ACTION_DOWN
      vdPos.dx,
      vdPos.dy,
      0, // Native에서 관리
      0,
    );
  }

  void _onPointerMove(PointerMoveEvent event) {
    if (_virtualDisplayId == null || !_isPointerDown || _cachedViewSize == null) return;
    
    final now = DateTime.now().millisecondsSinceEpoch;
    
    // Throttle: 최소 간격 제한 (과도한 이벤트 방지)
    if (now - _lastMoveTime < _moveThrottleMs) return;
    _lastMoveTime = now;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // 포인터 시각화 업데이트 (setState 없이 직접 수정 후 repaint)
    _currentPointerPosition = event.localPosition;
    _pointerTrail.add(event.localPosition);
    if (_pointerTrail.length > _maxTrailLength) {
      _pointerTrail.removeAt(0);
    }
    // CustomPainter만 다시 그리기 (전체 위젯 리빌드 방지)
    (context as Element).markNeedsBuild();
    
    // ACTION_MOVE 전송 (fire-and-forget)
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      2, // ACTION_MOVE
      vdPos.dx,
      vdPos.dy,
      0, // Native에서 관리
      0,
    );
  }

  void _onPointerUp(PointerUpEvent event) {
    if (_virtualDisplayId == null || !_isPointerDown || _cachedViewSize == null) return;
    
    final vdPos = _localToVirtualDisplay(event.localPosition, _cachedViewSize!);
    
    // ACTION_UP 전송 (fire-and-forget)
    NativeService.injectMotionEvent(
      _virtualDisplayId!,
      1, // ACTION_UP
      vdPos.dx,
      vdPos.dy,
      0, // Native에서 관리
      0,
    );
    
    setState(() {
      _isPointerDown = false;
      _currentPointerPosition = null;
      _pointerTrail.clear();
    });
    
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
      0, // Native에서 관리
      0,
    );
    
    setState(() {
      _isPointerDown = false;
      _currentPointerPosition = null;
      _pointerTrail.clear();
    });
    
    _cachedViewSize = null;
  }

  void _showControlsTemporarily() {
    setState(() => _showControls = true);
    _controlsTimer?.cancel();
    _controlsTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) setState(() => _showControls = false);
    });
  }

  @override
  void dispose() {
    _controlsTimer?.cancel();
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
