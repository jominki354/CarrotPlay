import 'dart:async';
import 'package:flutter/scheduler.dart';
import 'package:flutter/services.dart';

/// 실시간 성능 모니터링 서비스
/// FPS, 프레임 드롭, 터치 지연 시간 등을 측정
class PerformanceMonitor {
  static final PerformanceMonitor _instance = PerformanceMonitor._internal();
  factory PerformanceMonitor() => _instance;
  PerformanceMonitor._internal();

  // FPS 측정
  int _frameCount = 0;
  double _fps = 0.0;
  double _avgFrameTime = 0.0;
  int _droppedFrames = 0;
  
  // 프레임 타이밍
  Duration _lastFrameDuration = Duration.zero;
  final List<Duration> _recentFrameTimes = [];
  static const int _maxFrameSamples = 60;
  
  // 터치 지연 측정
  int _touchLatencyUs = 0; // 마이크로초
  int _touchCount = 0;
  
  // 메모리 정보 (Native에서 가져옴)
  int _usedMemoryMb = 0;
  int _totalMemoryMb = 0;
  
  // PIP별 정보
  final Map<int, PipPerformanceInfo> _pipInfo = {};
  
  // 스트림
  final _controller = StreamController<PerformanceData>.broadcast();
  Stream<PerformanceData> get stream => _controller.stream;
  
  Timer? _updateTimer;
  bool _isRunning = false;
  
  /// 모니터링 시작
  void start() {
    if (_isRunning) return;
    _isRunning = true;
    
    // 프레임 콜백 등록
    SchedulerBinding.instance.addTimingsCallback(_onFrameTimings);
    
    // 주기적 업데이트 (1초마다 - 성능 개선)
    _updateTimer = Timer.periodic(const Duration(milliseconds: 1000), (_) {
      _calculateMetrics();
      _emitData();
    });
  }
  
  /// 모니터링 중지
  void stop() {
    _isRunning = false;
    _updateTimer?.cancel();
    _updateTimer = null;
    SchedulerBinding.instance.removeTimingsCallback(_onFrameTimings);
  }
  
  /// 프레임 타이밍 콜백
  void _onFrameTimings(List<FrameTiming> timings) {
    for (final timing in timings) {
      _frameCount++;
      
      // 프레임 렌더링 시간 (빌드 + 레이아웃 + 페인트)
      final buildDuration = timing.buildDuration;
      final rasterDuration = timing.rasterDuration;
      final totalDuration = buildDuration + rasterDuration;
      
      _lastFrameDuration = totalDuration;
      _recentFrameTimes.add(totalDuration);
      
      // 최대 샘플 수 유지
      if (_recentFrameTimes.length > _maxFrameSamples) {
        _recentFrameTimes.removeAt(0);
      }
      
      // 16.67ms (60fps) 초과 시 드롭 프레임
      if (totalDuration.inMicroseconds > 16667) {
        _droppedFrames++;
      }
    }
  }
  
  /// 메트릭 계산
  void _calculateMetrics() {
    // FPS 계산 (1초당 프레임 수)
    _fps = _frameCount * 1.0;
    _frameCount = 0;
    
    // 평균 프레임 시간 계산
    if (_recentFrameTimes.isNotEmpty) {
      final totalUs = _recentFrameTimes.fold<int>(
        0, (sum, d) => sum + d.inMicroseconds);
      _avgFrameTime = totalUs / _recentFrameTimes.length / 1000.0; // ms
    }
  }
  
  /// 데이터 전송
  void _emitData() {
    if (_controller.isClosed) return;
    
    _controller.add(PerformanceData(
      fps: _fps,
      avgFrameTimeMs: _avgFrameTime,
      droppedFrames: _droppedFrames,
      touchLatencyUs: _touchLatencyUs,
      touchCount: _touchCount,
      usedMemoryMb: _usedMemoryMb,
      totalMemoryMb: _totalMemoryMb,
      pipInfo: Map.from(_pipInfo),
    ));
  }
  
  /// 터치 지연 시간 기록 (Native에서 호출)
  void recordTouchLatency(int latencyUs) {
    _touchLatencyUs = latencyUs;
    _touchCount++;
  }
  
  /// PIP 정보 업데이트
  void updatePipInfo(int pipId, {
    int? displayId,
    String? appPackage,
    int? touchInjectTimeUs,
    int? frameRenderTimeUs,
  }) {
    final info = _pipInfo.putIfAbsent(pipId, () => PipPerformanceInfo(pipId: pipId));
    
    if (displayId != null) info.displayId = displayId;
    if (appPackage != null) info.appPackage = appPackage;
    if (touchInjectTimeUs != null) info.touchInjectTimeUs = touchInjectTimeUs;
    if (frameRenderTimeUs != null) info.frameRenderTimeUs = frameRenderTimeUs;
  }
  
  /// 메모리 정보 업데이트
  void updateMemoryInfo(int usedMb, int totalMb) {
    _usedMemoryMb = usedMb;
    _totalMemoryMb = totalMb;
  }
  
  /// 현재 성능 데이터 스냅샷
  PerformanceData get currentData => PerformanceData(
    fps: _fps,
    avgFrameTimeMs: _avgFrameTime,
    droppedFrames: _droppedFrames,
    touchLatencyUs: _touchLatencyUs,
    touchCount: _touchCount,
    usedMemoryMb: _usedMemoryMb,
    totalMemoryMb: _totalMemoryMb,
    pipInfo: Map.from(_pipInfo),
  );
  
  void dispose() {
    stop();
    _controller.close();
  }
}

/// 성능 데이터 클래스
class PerformanceData {
  final double fps;
  final double avgFrameTimeMs;
  final int droppedFrames;
  final int touchLatencyUs;
  final int touchCount;
  final int usedMemoryMb;
  final int totalMemoryMb;
  final Map<int, PipPerformanceInfo> pipInfo;
  
  PerformanceData({
    required this.fps,
    required this.avgFrameTimeMs,
    required this.droppedFrames,
    required this.touchLatencyUs,
    required this.touchCount,
    required this.usedMemoryMb,
    required this.totalMemoryMb,
    required this.pipInfo,
  });
  
  /// FPS 색상 (성능 상태 표시)
  int get fpsColor {
    if (fps >= 55) return 0xFF4CAF50; // 녹색 (좋음)
    if (fps >= 40) return 0xFFFF9800; // 주황 (보통)
    return 0xFFF44336; // 빨강 (나쁨)
  }
  
  /// 프레임 드롭 비율
  double get dropRate => touchCount > 0 ? droppedFrames / touchCount * 100 : 0;
}

/// PIP별 성능 정보
class PipPerformanceInfo {
  final int pipId;
  int displayId = -1;
  String appPackage = '';
  int touchInjectTimeUs = 0; // 터치 주입 시간 (마이크로초)
  int frameRenderTimeUs = 0; // 프레임 렌더링 시간
  
  PipPerformanceInfo({required this.pipId});
  
  /// 터치 주입 시간 (밀리초)
  double get touchInjectTimeMs => touchInjectTimeUs / 1000.0;
  
  /// 앱 이름 (짧게)
  String get appName => appPackage.isNotEmpty 
      ? appPackage.split('.').last 
      : '-';
}
