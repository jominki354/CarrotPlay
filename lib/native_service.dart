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
  // Touch Injection (Root 기반)
  // ============================================
  
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

  /// 롱프레스 주입 (같은 좌표로 긴 스와이프)
  static Future<bool> injectLongPress(int displayId, int x, int y) async {
    return injectSwipe(displayId, x, y, x, y, 800);
  }

  // ============================================
  // App Control (Root 기반)
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
}
