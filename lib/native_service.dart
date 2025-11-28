import 'package:flutter/services.dart';

class NativeService {
  static const MethodChannel _channel = MethodChannel('com.carcarlauncher.clone/launcher');

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
}
