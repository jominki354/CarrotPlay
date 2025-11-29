import 'package:flutter/services.dart';

class NativeService {
  static const MethodChannel _channel = MethodChannel('com.carcarlauncher.clone/launcher');
  
  // 터치 주입 결과 캐시 (성능 최적화)
  static bool? _hasRoot;

  static Future<Map<String, int>?> createVirtualDisplay(int width, int height, int density) async {
    try {
      print("NativeService: Creating VirtualDisplay ${width}x$height @ ${density}dpi");
      
      final result = await _channel.invokeMethod('createVirtualDisplay', {
        'width': width,
        'height': height,
        'density': density,
      });
      
      print("NativeService: VirtualDisplay result = $result");
      
      if (result != null) {
        return Map<String, int>.from(result);
      }
    } catch (e) {
      print("NativeService: Error creating virtual display: $e");
      rethrow;
    }
    return null;
  }

  static Future<bool> launchApp(String packageName, int displayId) async {
    try {
      print("NativeService: Launching $packageName on display $displayId");
      
      final result = await _channel.invokeMethod('launchApp', {
        'packageName': packageName,
        'displayId': displayId,
      });
      
      print("NativeService: Launch result = $result");
      return result == true;
    } catch (e) {
      print("NativeService: Error launching app: $e");
      return false;
    }
  }

  static Future<List<Map<String, dynamic>>> getInstalledApps() async {
    try {
      print("NativeService: Getting installed apps");
      
      final result = await _channel.invokeMethod('getInstalledApps');
      
      print("NativeService: Got ${result?.length ?? 0} apps");
      
      if (result != null) {
        final apps = List<Map<String, dynamic>>.from(
          result.map((item) => Map<String, dynamic>.from(item))
        );
        print("NativeService: Parsed ${apps.length} apps");
        return apps;
      }
    } catch (e) {
      print("NativeService: Error getting installed apps: $e");
      rethrow;
    }
    return [];
  }

  static Future<bool> checkRoot() async {
    try {
      final bool hasRoot = await _channel.invokeMethod('checkRoot');
      print('NativeService: Root check result: $hasRoot');
      return hasRoot;
    } catch (e) {
      print("NativeService: Error checking root: $e");
      return false;
    }
  }

  static Future<bool> installAsSystemApp() async {
    try {
      print('NativeService: Installing as system app...');
      final bool success = await _channel.invokeMethod('installAsSystemApp');
      print('NativeService: Installation result: $success');
      return success;
    } catch (e) {
      print("NativeService: Error installing as system app: $e");
      return false;
    }
  }

  // ============================================
  // Touch Injection (System API 우선, Root fallback)
  // ============================================
  
  /// 실시간 MotionEvent 주입 (최적화 버전)
  /// action: 0=DOWN, 1=UP, 2=MOVE, 3=CANCEL
  static void injectMotionEvent(
    int displayId,
    int action,
    double x,
    double y,
    int downTime,
    int eventTime,
  ) {
    // Fire-and-forget: 결과 대기 안 함
    _channel.invokeMethod('injectMotionEvent', {
      'displayId': displayId,
      'action': action,
      'x': x,
      'y': y,
    });
  }
  
  /// 단일 탭 주입
  static Future<bool> injectTap(int displayId, int x, int y) async {
    try {
      final result = await _channel.invokeMethod('injectTap', {
        'displayId': displayId,
        'x': x,
        'y': y,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error injecting tap: $e");
      return false;
    }
  }

  /// 스와이프 주입
  static Future<bool> injectSwipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) async {
    try {
      final result = await _channel.invokeMethod('injectSwipe', {
        'displayId': displayId,
        'x1': x1,
        'y1': y1,
        'x2': x2,
        'y2': y2,
        'duration': durationMs,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error injecting swipe: $e");
      return false;
    }
  }

  /// 롱프레스 주입 (Native에서 처리)
  static Future<bool> injectLongPress(int displayId, int x, int y, {int durationMs = 800}) async {
    try {
      final result = await _channel.invokeMethod('injectLongPress', {
        'displayId': displayId,
        'x': x,
        'y': y,
        'duration': durationMs,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error injecting long press: $e");
      return false;
    }
  }

  // ============================================
  // App Control
  // ============================================
  
  /// Back 키 전송
  static Future<bool> sendBackKey(int displayId) async {
    try {
      final result = await _channel.invokeMethod('sendKeyEvent', {
        'displayId': displayId,
        'keyCode': 4, // KEYCODE_BACK
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error sending back key: $e");
      return false;
    }
  }

  /// Home 키 전송
  static Future<bool> sendHomeKey(int displayId) async {
    try {
      final result = await _channel.invokeMethod('sendKeyEvent', {
        'displayId': displayId,
        'keyCode': 3, // KEYCODE_HOME
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error sending home key: $e");
      return false;
    }
  }

  /// Recent Apps 키 전송
  static Future<bool> sendRecentKey(int displayId) async {
    try {
      final result = await _channel.invokeMethod('sendKeyEvent', {
        'displayId': displayId,
        'keyCode': 187, // KEYCODE_APP_SWITCH
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error sending recent key: $e");
      return false;
    }
  }

  /// 앱 강제 종료
  static Future<bool> forceStopApp(String packageName) async {
    try {
      final result = await _channel.invokeMethod('forceStopApp', {
        'packageName': packageName,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error force stopping app: $e");
      return false;
    }
  }

  /// 앱을 메인 디스플레이로 이동 (전체화면)
  static Future<bool> moveToMainDisplay(String packageName) async {
    try {
      final result = await _channel.invokeMethod('moveToMainDisplay', {
        'packageName': packageName,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error moving app: $e");
      return false;
    }
  }

  /// 가상 디스플레이 크기/DPI 조절 (원본 앱 방식)
  static Future<bool> resizeVirtualDisplay(int displayId, int width, int height, int density) async {
    try {
      print("NativeService: Resizing VirtualDisplay $displayId to ${width}x$height @ ${density}dpi");
      final result = await _channel.invokeMethod('resizeVirtualDisplay', {
        'displayId': displayId,
        'width': width,
        'height': height,
        'density': density,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error resizing virtual display: $e");
      return false;
    }
  }

  /// 가상 디스플레이 해제
  static Future<bool> releaseVirtualDisplay(int displayId) async {
    try {
      print("NativeService: Releasing VirtualDisplay $displayId");
      final result = await _channel.invokeMethod('releaseVirtualDisplay', {
        'displayId': displayId,
      });
      return result == true;
    } catch (e) {
      print("NativeService: Error releasing virtual display: $e");
      return false;
    }
  }
  
  /// 디스플레이에서 실행 중인 앱 확인
  static Future<String?> getTopActivity(int displayId) async {
    try {
      final result = await _channel.invokeMethod('getTopActivity', {
        'displayId': displayId,
      });
      return result as String?;
    } catch (e) {
      // 에러 무시
      return null;
    }
  }
  
  /// 뒤로가기 가능 여부 확인 (앱 종료 방지)
  /// 앱의 Activity 백스택이 1개 이상이면 true
  static Future<bool> canGoBack(int displayId) async {
    try {
      final result = await _channel.invokeMethod('canGoBack', {
        'displayId': displayId,
      });
      return result == true;
    } catch (e) {
      // 에러 시 기본적으로 허용 (안전한 기본값)
      print("NativeService: Error checking canGoBack: $e");
      return true;
    }
  }
}
