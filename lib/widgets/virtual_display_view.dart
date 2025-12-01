import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// VirtualDisplay를 Native PlatformView로 렌더링하는 위젯
/// 
/// 원본 CarCarLauncher 앱처럼 터치를 Native에서 직접 처리합니다.
/// Flutter의 터치 이벤트 변환 과정을 거치지 않아 터치 인식이 100% 정확합니다.
class VirtualDisplayView extends StatefulWidget {
  final int width;
  final int height;
  final int dpi;
  final Function(int displayId)? onDisplayCreated;
  final VoidCallback? onError;
  
  const VirtualDisplayView({
    super.key,
    required this.width,
    required this.height,
    this.dpi = 320,
    this.onDisplayCreated,
    this.onError,
  });
  
  @override
  State<VirtualDisplayView> createState() => VirtualDisplayViewState();
}

class VirtualDisplayViewState extends State<VirtualDisplayView> {
  static const String viewType = 'android.test.settings/virtual_display_view';
  static const MethodChannel _channel = MethodChannel('android.test.settings/virtual_display_view_channel');
  
  int? _displayId;
  bool _isReady = false;
  int _viewId = -1;
  
  int? get displayId => _displayId;
  bool get isReady => _isReady;
  
  @override
  void initState() {
    super.initState();
    // AndroidView가 생성된 후 displayId를 가져옵니다
  }
  
  /// AndroidView 생성 시 호출되는 콜백
  Future<void> _onPlatformViewCreated(int viewId) async {
    print('[VDView] PlatformView created: viewId=$viewId');
    _viewId = viewId;
    
    // displayId 가져오기 (SurfaceTexture가 준비될 때까지 대기)
    await _waitForDisplayId();
  }
  
  /// displayId가 준비될 때까지 대기
  Future<void> _waitForDisplayId() async {
    for (int i = 0; i < 50; i++) { // 최대 5초 대기
      try {
        final displayId = await _channel.invokeMethod<int>('getDisplayId', {
          'viewId': _viewId,
        });
        
        if (displayId != null && displayId > 0) {
          print('[VDView] Display ready: displayId=$displayId');
          setState(() {
            _displayId = displayId;
            _isReady = true;
          });
          widget.onDisplayCreated?.call(displayId);
          return;
        }
      } catch (e) {
        print('[VDView] Waiting for displayId... (attempt ${i + 1})');
      }
      await Future.delayed(const Duration(milliseconds: 100));
    }
    
    print('[VDView] Failed to get displayId after 5 seconds');
    widget.onError?.call();
  }
  
  /// 앱 실행
  Future<bool> launchApp(String packageName) async {
    if (_viewId < 0 || !_isReady) {
      print('[VDView] Cannot launch app: view not ready');
      return false;
    }
    
    try {
      final result = await _channel.invokeMethod<bool>('launchApp', {
        'viewId': _viewId,
        'packageName': packageName,
      });
      return result ?? false;
    } catch (e) {
      print('[VDView] Failed to launch app: $e');
      return false;
    }
  }
  
  /// VirtualDisplay 크기 변경
  Future<bool> resize(int width, int height, int dpi) async {
    if (_viewId < 0) return false;
    
    try {
      final result = await _channel.invokeMethod<bool>('resize', {
        'viewId': _viewId,
        'width': width,
        'height': height,
        'dpi': dpi,
      });
      return result ?? false;
    } catch (e) {
      print('[VDView] Failed to resize: $e');
      return false;
    }
  }
  
  @override
  Widget build(BuildContext context) {
    // creationParams: Native로 전달할 초기 파라미터
    final creationParams = <String, dynamic>{
      'width': widget.width,
      'height': widget.height,
      'dpi': widget.dpi,
    };
    
    return AndroidView(
      viewType: viewType,
      creationParams: creationParams,
      creationParamsCodec: const StandardMessageCodec(),
      onPlatformViewCreated: _onPlatformViewCreated,
      // Hybrid Composition 사용 (TextureLayer보다 터치가 정확함)
      // gestureRecognizers는 비워두면 Native에서 모든 터치를 처리
    );
  }
  
  @override
  void dispose() {
    // PlatformView dispose
    if (_viewId >= 0) {
      _channel.invokeMethod('dispose', {'viewId': _viewId});
    }
    super.dispose();
  }
}
