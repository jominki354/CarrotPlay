import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

/// NativePipService - Native PIP 컨테이너와 통신
/// 
/// 방법 B: XML 레이아웃에서 FlutterView와 NativePipContainer를 같은 레벨에 배치
/// - FlutterView: 전체 화면 (Dock + 디버그바 + PIP 제스처바 등)
/// - NativePipContainer: 하단 PIP 영역 (터치 직접 처리)
/// 
/// Z-order 충돌 없이 명확한 영역 분리
/// Flutter에서 PIP 영역은 투명하게 비우고, Native가 그 위치에 표시
class NativePipService {
  static const _channel = MethodChannel('com.carcarlauncher.clone/native_pip');
  
  static final NativePipService _instance = NativePipService._internal();
  factory NativePipService() => _instance;
  NativePipService._internal();
  
  bool _enabled = false;
  bool get isEnabled => _enabled;
  
  double _currentRatio = 0.5;
  double get currentRatio => _currentRatio;
  
  /// Native PIP 모드 활성화 (방법 B)
  /// screenWidth/Height: 전체 화면 크기 (px)
  /// pipHeight: PIP 영역 높이 (px)
  Future<bool> enable({
    required int screenWidth,
    required int screenHeight,
    required int pipHeight,
  }) async {
    try {
      final result = await _channel.invokeMethod<bool>('setEnabled', {
        'enabled': true,
        'screenWidth': screenWidth,
        'screenHeight': screenHeight,
        'pipHeight': pipHeight,
      });
      _enabled = result ?? false;
      debugPrint('[NativePipService] enabled: $_enabled (method B)');
      return _enabled;
    } catch (e) {
      debugPrint('[NativePipService] enable error: $e');
      return false;
    }
  }
  
  /// Native PIP 모드 비활성화
  Future<void> disable() async {
    try {
      await _channel.invokeMethod<bool>('setEnabled', {
        'enabled': false,
      });
      _enabled = false;
      debugPrint('[NativePipService] disabled');
    } catch (e) {
      debugPrint('[NativePipService] disable error: $e');
    }
  }
  
  /// PIP 높이 설정 (Flutter에서 계산된 PIP 영역 높이)
  Future<void> setPipHeight(int height) async {
    if (!_enabled) return;
    
    try {
      await _channel.invokeMethod('setPipHeight', {
        'height': height,
      });
      debugPrint('[NativePipService] setPipHeight: $height');
    } catch (e) {
      debugPrint('[NativePipService] setPipHeight error: $e');
    }
  }
  
  /// PIP 비율 설정 (왼쪽 PIP 비율) - 즉시 적용
  Future<void> setRatio(double ratio) async {
    if (!_enabled) return;
    
    _currentRatio = ratio;
    try {
      await _channel.invokeMethod('setRatio', {
        'ratio': ratio,
      });
    } catch (e) {
      debugPrint('[NativePipService] setRatio error: $e');
    }
  }
  
  /// PIP 비율 설정 (애니메이션 적용) - 부드러운 전환
  /// [durationMs] 애니메이션 시간 (기본 150ms)
  Future<void> setRatioAnimated(double ratio, {int durationMs = 150}) async {
    if (!_enabled) return;
    
    _currentRatio = ratio;
    try {
      // 결과 대기하지 않음 (fire and forget)
      _channel.invokeMethod('setRatioAnimated', {
        'ratio': ratio,
        'durationMs': durationMs,
      });
    } catch (e) {
      debugPrint('[NativePipService] setRatioAnimated error: $e');
    }
  }
  
  /// PIP에 앱 실행
  /// pipIndex: 0 = 왼쪽, 1 = 오른쪽
  Future<bool> launchApp(int pipIndex, String packageName) async {
    if (!_enabled) return false;
    
    try {
      final result = await _channel.invokeMethod<bool>('launchApp', {
        'pipIndex': pipIndex,
        'packageName': packageName,
      });
      debugPrint('[NativePipService] launchApp pip$pipIndex: $packageName -> $result');
      return result ?? false;
    } catch (e) {
      debugPrint('[NativePipService] launchApp error: $e');
      return false;
    }
  }
  
  /// PIP에 뒤로가기 키 전송
  Future<void> sendBackKey(int pipIndex) async {
    if (!_enabled) return;
    
    try {
      await _channel.invokeMethod('sendBackKey', {
        'pipIndex': pipIndex,
      });
    } catch (e) {
      debugPrint('[NativePipService] sendBackKey error: $e');
    }
  }
  
  /// PIP의 VirtualDisplay ID 조회
  Future<int> getDisplayId(int pipIndex) async {
    if (!_enabled) return -1;
    
    try {
      final result = await _channel.invokeMethod<int>('getDisplayId', {
        'pipIndex': pipIndex,
      });
      return result ?? -1;
    } catch (e) {
      debugPrint('[NativePipService] getDisplayId error: $e');
      return -1;
    }
  }
  
  /// PIP의 현재 실행 중인 앱 패키지 조회
  Future<String?> getCurrentPackage(int pipIndex) async {
    if (!_enabled) return null;
    
    try {
      final result = await _channel.invokeMethod<String?>('getCurrentPackage', {
        'pipIndex': pipIndex,
      });
      return result;
    } catch (e) {
      debugPrint('[NativePipService] getCurrentPackage error: $e');
      return null;
    }
  }
  
  /// 크기 업데이트 (화면 회전 등)
  Future<void> updateSize({
    required int width,
    required int height,
    required int pipHeight,
  }) async {
    if (!_enabled) return;
    
    try {
      await _channel.invokeMethod('updateSize', {
        'width': width,
        'height': height,
        'pipHeight': pipHeight,
      });
    } catch (e) {
      debugPrint('[NativePipService] updateSize error: $e');
    }
  }
}
