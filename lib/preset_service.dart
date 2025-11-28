import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// PIP에 지정된 앱 정보
class PipAppConfig {
  final String? packageName;
  final String? appName;
  final Uint8List? icon;
  final double scale; // 크기 비율 (0.5 ~ 1.5)

  PipAppConfig({
    this.packageName,
    this.appName,
    this.icon,
    this.scale = 1.0,
  });

  bool get isEmpty => packageName == null || packageName!.isEmpty;
  bool get isNotEmpty => !isEmpty;

  Map<String, dynamic> toJson() => {
    'packageName': packageName,
    'appName': appName,
    'icon': icon != null ? base64Encode(icon!) : null,
    'scale': scale,
  };

  factory PipAppConfig.fromJson(Map<String, dynamic> json) => PipAppConfig(
    packageName: json['packageName'],
    appName: json['appName'],
    icon: json['icon'] != null ? base64Decode(json['icon']) : null,
    scale: (json['scale'] ?? 1.0).toDouble(),
  );

  PipAppConfig copyWith({
    String? packageName,
    String? appName,
    Uint8List? icon,
    double? scale,
  }) => PipAppConfig(
    packageName: packageName ?? this.packageName,
    appName: appName ?? this.appName,
    icon: icon ?? this.icon,
    scale: scale ?? this.scale,
  );
}

/// 좌우 비율 옵션 (원본 앱처럼 9가지)
/// left30Right70 ~ left70Right30
class SplitRatio {
  final double leftRatio;
  final String label;
  
  const SplitRatio(this.leftRatio, this.label);
  
  double get rightRatio => 1.0 - leftRatio;
  
  // 9가지 비율 옵션
  static const List<SplitRatio> options = [
    SplitRatio(0.30, '30:70'),
    SplitRatio(0.35, '35:65'),
    SplitRatio(0.40, '40:60'),
    SplitRatio(0.45, '45:55'),
    SplitRatio(0.50, '50:50'),
    SplitRatio(0.55, '55:45'),
    SplitRatio(0.60, '60:40'),
    SplitRatio(0.65, '65:35'),
    SplitRatio(0.70, '70:30'),
  ];
  
  static SplitRatio fromLeftRatio(double ratio) {
    // 가장 가까운 옵션 찾기
    return options.reduce((a, b) => 
      (a.leftRatio - ratio).abs() < (b.leftRatio - ratio).abs() ? a : b);
  }
}

/// 프리셋 설정 (PIP 1, 2에 어떤 앱을 실행할지 + 좌우 비율)
class PresetConfig {
  final String name;
  final PipAppConfig pip1;
  final PipAppConfig pip2;
  final double leftRatio; // 왼쪽 PIP 영역 비율 (0.3 ~ 0.7)

  PresetConfig({
    required this.name,
    PipAppConfig? pip1,
    PipAppConfig? pip2,
    this.leftRatio = 0.5, // 기본값 50:50
  }) : pip1 = pip1 ?? PipAppConfig(),
       pip2 = pip2 ?? PipAppConfig();

  bool get isEmpty => pip1.isEmpty && pip2.isEmpty;
  bool get isNotEmpty => !isEmpty;
  
  double get rightRatio => 1.0 - leftRatio;
  SplitRatio get splitRatio => SplitRatio.fromLeftRatio(leftRatio);

  Map<String, dynamic> toJson() => {
    'name': name,
    'pip1': pip1.toJson(),
    'pip2': pip2.toJson(),
    'leftRatio': leftRatio,
  };

  factory PresetConfig.fromJson(Map<String, dynamic> json) => PresetConfig(
    name: json['name'] ?? 'Preset',
    pip1: json['pip1'] != null ? PipAppConfig.fromJson(json['pip1']) : null,
    pip2: json['pip2'] != null ? PipAppConfig.fromJson(json['pip2']) : null,
    leftRatio: (json['leftRatio'] ?? 0.5).toDouble(),
  );

  PresetConfig copyWith({
    String? name,
    PipAppConfig? pip1,
    PipAppConfig? pip2,
    double? leftRatio,
  }) => PresetConfig(
    name: name ?? this.name,
    pip1: pip1 ?? this.pip1,
    pip2: pip2 ?? this.pip2,
    leftRatio: leftRatio ?? this.leftRatio,
  );
}

/// 프리셋 관리 서비스
class PresetService extends ChangeNotifier {
  static final PresetService _instance = PresetService._internal();
  factory PresetService() => _instance;
  PresetService._internal();

  List<PresetConfig> _presets = [
    PresetConfig(name: '1'),
    PresetConfig(name: '2'),
    PresetConfig(name: '3'),
  ];

  int _selectedIndex = 0;

  List<PresetConfig> get presets => _presets;
  int get selectedIndex => _selectedIndex;
  PresetConfig get currentPreset => _presets[_selectedIndex];

  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final data = prefs.getString('presets');
      if (data != null) {
        final List<dynamic> list = jsonDecode(data);
        _presets = list.map((e) => PresetConfig.fromJson(e)).toList();
        // 프리셋이 3개 미만이면 채우기
        while (_presets.length < 3) {
          _presets.add(PresetConfig(name: '${_presets.length + 1}'));
        }
      }
      _selectedIndex = prefs.getInt('selectedPreset') ?? 0;
      notifyListeners();
    } catch (e) {
      debugPrint('Failed to load presets: $e');
    }
  }

  Future<void> save() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('presets', jsonEncode(_presets.map((e) => e.toJson()).toList()));
      await prefs.setInt('selectedPreset', _selectedIndex);
    } catch (e) {
      debugPrint('Failed to save presets: $e');
    }
  }

  void selectPreset(int index) {
    if (index >= 0 && index < _presets.length) {
      _selectedIndex = index;
      notifyListeners();
      save();
    }
  }

  void updatePreset(int index, PresetConfig config) {
    if (index >= 0 && index < _presets.length) {
      _presets[index] = config;
      notifyListeners();
      save();
    }
  }

  void updatePip1(int presetIndex, PipAppConfig config) {
    if (presetIndex >= 0 && presetIndex < _presets.length) {
      _presets[presetIndex] = _presets[presetIndex].copyWith(pip1: config);
      notifyListeners();
      save();
    }
  }

  void updatePip2(int presetIndex, PipAppConfig config) {
    if (presetIndex >= 0 && presetIndex < _presets.length) {
      _presets[presetIndex] = _presets[presetIndex].copyWith(pip2: config);
      notifyListeners();
      save();
    }
  }

  void updateLeftRatio(int presetIndex, double ratio) {
    if (presetIndex >= 0 && presetIndex < _presets.length) {
      // 0.3 ~ 0.7 범위로 제한
      final clampedRatio = ratio.clamp(0.3, 0.7);
      _presets[presetIndex] = _presets[presetIndex].copyWith(leftRatio: clampedRatio);
      notifyListeners();
      save();
    }
  }

  void setRatioFromOptions(int presetIndex, int optionIndex) {
    if (optionIndex >= 0 && optionIndex < SplitRatio.options.length) {
      updateLeftRatio(presetIndex, SplitRatio.options[optionIndex].leftRatio);
    }
  }
}
