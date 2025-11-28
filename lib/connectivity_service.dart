import 'dart:async';
import 'package:flutter/services.dart';

class NetworkStatus {
  final bool isConnected;
  final bool isWifi;
  final String networkName;
  final int signalStrength;

  NetworkStatus({
    this.isConnected = false,
    this.isWifi = false,
    this.networkName = '-',
    this.signalStrength = 0,
  });
}

class ConnectivityService {
  static const _channel = MethodChannel('android.test.settings/connectivity');
  
  final _statusController = StreamController<NetworkStatus>.broadcast();
  Stream<NetworkStatus> get statusStream => _statusController.stream;
  
  NetworkStatus _currentStatus = NetworkStatus();
  NetworkStatus get currentStatus => _currentStatus;
  
  Timer? _pollTimer;

  void init() {
    _updateNetworkStatus();
    // 5초마다 네트워크 상태 업데이트
    _pollTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      _updateNetworkStatus();
    });
  }

  void dispose() {
    _pollTimer?.cancel();
    _statusController.close();
  }

  Future<void> _updateNetworkStatus() async {
    try {
      final result = await _channel.invokeMethod('getNetworkStatus');
      if (result != null) {
        _currentStatus = NetworkStatus(
          isConnected: result['isConnected'] ?? false,
          isWifi: result['isWifi'] ?? false,
          networkName: result['networkName'] ?? '-',
          signalStrength: result['signalStrength'] ?? 0,
        );
        _statusController.add(_currentStatus);
      }
    } catch (e) {
      // 네이티브 메서드 없으면 기본값 사용
      _currentStatus = NetworkStatus(
        isConnected: true,
        isWifi: false,
        networkName: 'SKT',
        signalStrength: 4,
      );
      _statusController.add(_currentStatus);
    }
  }
}
