import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'pip_view.dart';
import 'app_drawer_content.dart';
import 'connectivity_service.dart';
import 'preset_service.dart';
import 'preset_editor.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Timer _clockTimer;
  String _currentTime = '';
  final ConnectivityService _connectivity = ConnectivityService();
  final PresetService _presetService = PresetService();
  
  // PipView 컨트롤러 (앱 실행용)
  final GlobalKey<PipViewState> _pip1Key = GlobalKey();
  final GlobalKey<PipViewState> _pip2Key = GlobalKey();
  
  // 전체화면 PipView (앱 서랍에서 앱 선택 시 사용)
  final GlobalKey<PipViewState> _fullscreenPipKey = GlobalKey();
  
  // 앱 서랍 표시 여부
  bool _showAppDrawer = false;
  
  // 전체화면 앱 모드 (앱 서랍에서 앱 선택 후)
  bool _showFullscreenApp = false;
  String? _fullscreenAppPackage;

  @override
  void initState() {
    super.initState();
    _updateTime();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateTime());
    _connectivity.init();
    _presetService.load();
    _presetService.addListener(_onPresetChanged);
    
    // 앱 목록 미리 로드 (앱 서랍 로딩 지연 방지)
    AppCache.preload();
  }

  @override
  void dispose() {
    _clockTimer.cancel();
    _connectivity.dispose();
    _presetService.removeListener(_onPresetChanged);
    super.dispose();
  }

  void _onPresetChanged() {
    setState(() {});
  }

  void _updateTime() {
    final now = DateTime.now();
    setState(() {
      _currentTime = '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
    });
  }

  void _openAppDrawer() {
    // 앱 서랍을 오버레이로 표시 (Dock 오른쪽 영역에)
    setState(() {
      _showAppDrawer = true;
    });
  }
  
  void _closeAppDrawer() {
    setState(() {
      _showAppDrawer = false;
    });
  }
  
  /// 앱 서랍에서 앱 선택 시 호출 - 전체화면 모드로 전환
  void _launchFullscreenApp(String packageName) {
    setState(() {
      _showAppDrawer = false;
      _showFullscreenApp = true;
      _fullscreenAppPackage = packageName;
    });
    
    // 다음 프레임에서 앱 실행 (VirtualDisplay 생성 후)
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _fullscreenPipKey.currentState?.launchAppWithConfig(packageName);
    });
  }
  
  /// 전체화면 앱 닫기 - PIP 2개 모드로 복귀
  void _closeFullscreenApp() {
    setState(() {
      _showFullscreenApp = false;
      _fullscreenAppPackage = null;
    });
  }

  void _onPresetTap(int index) {
    final preset = _presetService.presets[index];
    
    // 비어있으면 설정창, 앱이 지정되어 있으면 실행
    if (preset.isEmpty) {
      _showPresetEditor(index);
    } else {
      // 앱서랍이 열려있으면 닫기
      if (_showAppDrawer) {
        _closeAppDrawer();
      }
      _presetService.selectPreset(index);
      _launchPresetApps(index);
    }
  }

  void _onPresetLongPress(int index) {
    // 길게 누르면 프리셋 편집 (앱이 있어도 편집 가능)
    _showPresetEditor(index);
  }
  
  void _showPresetEditor(int index) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => PresetEditor(
        presetIndex: index,
        presetService: _presetService,
      ),
    );
  }

  void _launchPresetApps(int index) {
    final preset = _presetService.presets[index];
    
    // PIP 1에 앱 실행
    if (preset.pip1.isNotEmpty) {
      _pip1Key.currentState?.launchAppWithConfig(
        preset.pip1.packageName!,
        scale: preset.pip1.scale,
      );
    }
    
    // PIP 2에 앱 실행
    if (preset.pip2.isNotEmpty) {
      _pip2Key.currentState?.launchAppWithConfig(
        preset.pip2.packageName!,
        scale: preset.pip2.scale,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    // 가로모드 아니면 로딩 표시
    if (MediaQuery.of(context).orientation != Orientation.landscape) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: const [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Switching to landscape mode...', 
                   style: TextStyle(color: Colors.white70)),
            ],
          ),
        ),
      );
    }
    
    // 뒤로가기로 앱 종료 방지
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, result) {
        // 앱 서랍이 열려있으면 닫기
        if (_showAppDrawer) {
          _closeAppDrawer();
          return;
        }
        // 전체화면 앱이 실행 중이면 PIP 모드로 복귀
        if (_showFullscreenApp) {
          _closeFullscreenApp();
          return;
        }
        // 그 외에는 아무것도 안 함 (앱 종료 방지)
      },
      child: Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            // Left Navigation Bar (Dock) - 항상 고정
            _buildDock(),
            
            // Right Area: 전체화면 VirtualDisplay (항상 존재) + PIP 오버레이
            Expanded(
              child: Stack(
                children: [
                  // 전체화면 VirtualDisplay (항상 백그라운드에 존재)
                  Positioned.fill(
                    child: PipView(
                      key: _fullscreenPipKey,
                      displayId: 0,
                      label: "",
                      isFullscreen: true,
                    ),
                  ),
                  
                  // PIP 모드일 때만 2개 PIP 오버레이
                  if (!_showFullscreenApp)
                    Positioned.fill(
                      child: _buildSplitPipArea(),
                    ),
                    
                  // 앱 서랍 오버레이
                  if (_showAppDrawer)
                    Positioned.fill(
                      child: AppDrawerContent(
                        onClose: _closeAppDrawer,
                        onAppSelected: _launchFullscreenApp,
                      ),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
      ),
    );
  }
  
  /// 분할 PIP 영역 (2개 PIP) - 현재 프리셋 비율 적용 + 드래그 가능한 구분선
  Widget _buildSplitPipArea() {
    final preset = _presetService.currentPreset;
    final leftFlex = (preset.leftRatio * 100).round();
    final rightFlex = (preset.rightRatio * 100).round();
    
    return Container(
      color: const Color(0xFF1A1A1A),
      child: LayoutBuilder(
        builder: (context, constraints) {
          return Stack(
            children: [
              // PIP 영역들
              Row(
                children: [
                  // PIP Area 1 (왼쪽)
                  Expanded(
                    flex: leftFlex,
                    child: PipView(
                      key: _pip1Key,
                      displayId: 1,
                      label: "",
                    ),
                  ),
                  // 구분선 공간 (제스처바 영역) - 여백 줄임
                  const SizedBox(width: 8),
                  // PIP Area 2 (오른쪽)
                  Expanded(
                    flex: rightFlex,
                    child: PipView(
                      key: _pip2Key,
                      displayId: 2,
                      label: "",
                    ),
                  ),
                ],
              ),
              
              // 드래그 가능한 구분선 (제스처바) - 1초 롱프레스 후 활성화
              Positioned(
                left: constraints.maxWidth * preset.leftRatio - 4,
                top: 0,
                bottom: 0,
                child: _RatioResizer(
                  maxWidth: constraints.maxWidth,
                  currentRatio: preset.leftRatio,
                  onRatioChanged: (newRatio) {
                    // 5단위로 스냅 (0.30, 0.35, 0.40, ...)
                    final snapped = (newRatio * 20).round() / 20.0;
                    final clampedRatio = snapped.clamp(0.3, 0.7);
                    _presetService.updateLeftRatio(_presetService.selectedIndex, clampedRatio);
                    setState(() {});
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  /// Dock (왼쪽 네비게이션 바) - 항상 고정
  Widget _buildDock() {
    return Container(
      width: 72,
      decoration: BoxDecoration(
        color: const Color(0xFF1A1A1A),
        border: Border(
          right: BorderSide(color: Colors.white10, width: 1),
        ),
      ),
      child: Column(
        children: [
          const SizedBox(height: 12),
          
          // 상단: 시간
          FittedBox(
            fit: BoxFit.scaleDown,
            child: Text(
              _currentTime,
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: Colors.white,
              ),
              maxLines: 1,
            ),
          ),
          
          const SizedBox(height: 4),
          
          // 네트워크 상태
          StreamBuilder<NetworkStatus>(
            stream: _connectivity.statusStream,
            initialData: _connectivity.currentStatus,
            builder: (context, snapshot) {
              final status = snapshot.data!;
              return Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        status.isWifi ? Icons.wifi : Icons.signal_cellular_alt,
                        size: 12,
                        color: status.isConnected ? Colors.white70 : Colors.white30,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        status.isWifi ? 'WiFi' : 'LTE',
                        style: TextStyle(
                          fontSize: 10,
                          color: status.isConnected ? Colors.white70 : Colors.white30,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 2),
                  SizedBox(
                    width: 64,
                    child: Text(
                      status.networkName,
                      style: const TextStyle(
                        fontSize: 9,
                        color: Colors.white54,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      textAlign: TextAlign.center,
                    ),
                  ),
                ],
              );
            },
          ),
          
          // 정중앙: 프리셋 3개
          Expanded(
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _buildPresetButton(0),
                  const SizedBox(height: 8),
                  _buildPresetButton(1),
                  const SizedBox(height: 8),
                  _buildPresetButton(2),
                ],
              ),
            ),
          ),
          
          // 하단: 앱서랍 버튼 또는 닫기 버튼
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: InkWell(
              onTap: () {
                if (_showFullscreenApp) {
                  _closeFullscreenApp();
                } else if (_showAppDrawer) {
                  _closeAppDrawer();
                } else {
                  _openAppDrawer();
                }
              },
              borderRadius: BorderRadius.circular(12),
              child: Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: (_showAppDrawer || _showFullscreenApp)
                      ? Colors.blueAccent.withOpacity(0.3)
                      : Colors.white.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                  border: (_showAppDrawer || _showFullscreenApp)
                      ? Border.all(color: Colors.blueAccent, width: 2)
                      : null,
                ),
                child: Icon(
                  _showFullscreenApp 
                      ? Icons.close 
                      : (_showAppDrawer ? Icons.close : Icons.apps),
                  color: Colors.white,
                  size: 28,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPresetButton(int index) {
    final isSelected = _presetService.selectedIndex == index;
    final preset = _presetService.presets[index];
    
    return GestureDetector(
      onTap: () => _onPresetTap(index),
      onLongPress: () => _onPresetLongPress(index),
      child: Container(
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: isSelected 
              ? Colors.blueAccent.withOpacity(0.3) 
              : Colors.white.withOpacity(0.05),
          borderRadius: BorderRadius.circular(12),
          border: isSelected 
              ? Border.all(color: Colors.blueAccent, width: 2)
              : Border.all(color: Colors.white24, width: 1),
        ),
        child: _buildRatioIndicator(preset, isSelected),
      ),
    );
  }

  /// 원본 앱처럼 좌우 비율을 시각적으로 표시하는 위젯
  Widget _buildRatioIndicator(PresetConfig preset, bool isSelected) {
    final leftRatio = preset.leftRatio;
    final rightRatio = preset.rightRatio;
    
    // 비율 라벨 (예: "50:50")
    final leftPercent = (leftRatio * 100).round();
    final rightPercent = (rightRatio * 100).round();
    
    return Padding(
      padding: const EdgeInsets.all(6),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // 비율 막대 (원본 앱 스타일)
          Expanded(
            child: Row(
              children: [
                // 왼쪽 영역 (pip1)
                Expanded(
                  flex: (leftRatio * 100).round(),
                  child: Container(
                    margin: const EdgeInsets.only(right: 1),
                    decoration: BoxDecoration(
                      color: preset.pip1.isNotEmpty 
                          ? Colors.blueAccent.withOpacity(0.5)
                          : Colors.white.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: preset.pip1.icon != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: Image.memory(preset.pip1.icon!, fit: BoxFit.cover),
                          )
                        : null,
                  ),
                ),
                // 오른쪽 영역 (pip2)
                Expanded(
                  flex: (rightRatio * 100).round(),
                  child: Container(
                    margin: const EdgeInsets.only(left: 1),
                    decoration: BoxDecoration(
                      color: preset.pip2.isNotEmpty 
                          ? Colors.greenAccent.withOpacity(0.5)
                          : Colors.white.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: preset.pip2.icon != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: Image.memory(preset.pip2.icon!, fit: BoxFit.cover),
                          )
                        : null,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          // 비율 텍스트 (아래)
          Text(
            '$leftPercent:$rightPercent',
            style: TextStyle(
              fontSize: 8,
              fontWeight: FontWeight.w500,
              color: isSelected ? Colors.white : Colors.white54,
            ),
          ),
        ],
      ),
    );
  }

  IconData _getAppIconFromPackage(String packageName) {
    if (packageName.contains('netflix')) return Icons.movie;
    if (packageName.contains('spotify') || packageName.contains('music')) return Icons.music_note;
    if (packageName.contains('map') || packageName.contains('navi')) return Icons.map;
    if (packageName.contains('youtube')) return Icons.play_circle;
    return Icons.android;
  }
}

/// 비율 조절 제스처바 - 1초 롱프레스 후 드래그 가능
class _RatioResizer extends StatefulWidget {
  final double maxWidth;
  final double currentRatio;
  final ValueChanged<double> onRatioChanged;

  const _RatioResizer({
    required this.maxWidth,
    required this.currentRatio,
    required this.onRatioChanged,
  });

  @override
  State<_RatioResizer> createState() => _RatioResizerState();
}

class _RatioResizerState extends State<_RatioResizer> {
  bool _isDragEnabled = false;
  bool _isHovering = false;
  Timer? _longPressTimer;

  @override
  void dispose() {
    _longPressTimer?.cancel();
    super.dispose();
  }

  void _startLongPressTimer() {
    _longPressTimer?.cancel();
    _longPressTimer = Timer(const Duration(seconds: 1), () {
      if (mounted) {
        setState(() {
          _isDragEnabled = true;
        });
        // 햅틱 피드백
        HapticFeedback.mediumImpact();
      }
    });
  }

  void _cancelLongPress() {
    _longPressTimer?.cancel();
    if (!_isDragEnabled) {
      setState(() {
        _isDragEnabled = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onPanStart: (details) {
        _startLongPressTimer();
      },
      onPanUpdate: (details) {
        if (_isDragEnabled) {
          // 드래그 활성화 시 비율 조절
          final newRatio = (details.globalPosition.dx - 72) / widget.maxWidth;
          widget.onRatioChanged(newRatio);
        }
      },
      onPanEnd: (details) {
        _longPressTimer?.cancel();
        setState(() {
          _isDragEnabled = false;
        });
      },
      onPanCancel: () {
        _cancelLongPress();
      },
      child: MouseRegion(
        cursor: _isDragEnabled ? SystemMouseCursors.resizeColumn : SystemMouseCursors.basic,
        onEnter: (_) => setState(() => _isHovering = true),
        onExit: (_) => setState(() => _isHovering = false),
        child: Container(
          width: 8,
          color: Colors.transparent,
          child: Center(
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              width: _isDragEnabled ? 6 : 4,
              height: _isDragEnabled ? 64 : 48,
              decoration: BoxDecoration(
                color: _isDragEnabled 
                    ? Colors.blueAccent 
                    : (_isHovering ? Colors.white38 : Colors.white24),
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
