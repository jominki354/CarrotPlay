import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'pip_view.dart';
import 'app_drawer_content.dart';
import 'connectivity_service.dart';
import 'preset_service.dart';
import 'preset_editor.dart';
import 'native_service.dart';
import 'theme/app_colors.dart';
import 'theme/app_dimens.dart';
import 'widgets/animations/bouncy_button.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> with TickerProviderStateMixin {
  late Timer _clockTimer;
  String _currentTime = '';
  final ConnectivityService _connectivity = ConnectivityService();
  final PresetService _presetService = PresetService();
  
  // PipView 컨트롤러 (앱 실행용)
  final GlobalKey<PipViewState> _pip1Key = GlobalKey();
  final GlobalKey<PipViewState> _pip2Key = GlobalKey();
  
  // 전체화면 PipView (앱 서랍에서 앱 선택 시 사용)
  final GlobalKey<PipViewState> _fullscreenPipKey = GlobalKey();
  
  // 앱 서랍 표시 여부 및 애니메이션
  bool _showAppDrawer = false;
  late AnimationController _drawerAnimController;
  late Animation<double> _drawerSlideAnim;
  late Animation<double> _drawerFadeAnim;
  
  // PIP 앱서랍 (바깥에서 올라오는 오버레이)
  bool _showPipDrawerOverlay = false;
  GlobalKey<PipViewState>? _pipDrawerTargetKey;
  late AnimationController _pipDrawerAnimController;
  late Animation<double> _pipDrawerSlideAnim;
  
  // 전체화면 앱 모드 (앱 서랍에서 앱 선택 후)
  bool _showFullscreenApp = false;
  String? _fullscreenAppPackage;
  
  // 디버그 오버레이
  bool _showDebugOverlay = true; // 지금은 항상 보이게
  late Timer _debugTimer;
  Map<String, dynamic> _debugInfo = {};

  @override
  void initState() {
    super.initState();
    _updateTime();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateTime());
    _connectivity.init();
    _presetService.load();
    _presetService.addListener(_onPresetChanged);
    
    // 앱 서랍 애니메이션 컨트롤러 (UI/UX 가이드라인: 300ms, easeOutQuart)
    _drawerAnimController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _drawerSlideAnim = Tween<double>(begin: 1.0, end: 0.0).animate(
      CurvedAnimation(parent: _drawerAnimController, curve: Curves.easeOutQuart),
    );
    _drawerFadeAnim = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _drawerAnimController, curve: Curves.easeOutQuart),
    );
    
    // PIP 앱서랍 애니메이션 컨트롤러
    _pipDrawerAnimController = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _pipDrawerSlideAnim = Tween<double>(begin: 1.0, end: 0.0).animate(
      CurvedAnimation(parent: _pipDrawerAnimController, curve: Curves.easeOutQuart),
    );
    
    // 앱 목록 새로고침 (앱 캐시 리셋 후 다시 로드)
    _refreshAppCache();
    
    // 디버그 오버레이 타이머 (1초마다 업데이트)
    _debugTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateDebugInfo());
  }
  
  Future<void> _refreshAppCache() async {
    await AppCache().refresh();
    debugPrint('AppCache refreshed, total apps: ${AppCache().apps.length}');
  }

  @override
  void dispose() {
    _clockTimer.cancel();
    _debugTimer.cancel();
    _connectivity.dispose();
    _presetService.removeListener(_onPresetChanged);
    _drawerAnimController.dispose();
    _pipDrawerAnimController.dispose();
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
  
  void _updateDebugInfo() {
    if (!_showDebugOverlay || !mounted) return;
    
    final mediaQuery = MediaQuery.of(context);
    final screenSize = mediaQuery.size;
    final pixelRatio = mediaQuery.devicePixelRatio;
    final physicalSize = Size(
      screenSize.width * pixelRatio,
      screenSize.height * pixelRatio,
    );
    
    // PIP 정보 수집
    final pip1State = _pip1Key.currentState;
    final pip2State = _pip2Key.currentState;
    final fullscreenState = _fullscreenPipKey.currentState;
    
    setState(() {
      _debugInfo = {
        // 화면 정보
        'screen': '${screenSize.width.toInt()}x${screenSize.height.toInt()}',
        'physical': '${physicalSize.width.toInt()}x${physicalSize.height.toInt()}',
        'dpr': pixelRatio.toStringAsFixed(2),
        'dpi': (160 * pixelRatio).toInt(),
        'orientation': mediaQuery.orientation == Orientation.landscape ? 'Landscape' : 'Portrait',
        
        // PIP 1 정보
        'pip1_vd': pip1State?.virtualDisplayId?.toString() ?? '-',
        'pip1_app': pip1State?.currentPackage?.split('.').last ?? '-',
        
        // PIP 2 정보
        'pip2_vd': pip2State?.virtualDisplayId?.toString() ?? '-',
        'pip2_app': pip2State?.currentPackage?.split('.').last ?? '-',
        
        // 전체화면 정보
        'fs_vd': fullscreenState?.virtualDisplayId?.toString() ?? '-',
        'fs_app': _showFullscreenApp ? (_fullscreenAppPackage?.split('.').last ?? '-') : '-',
        
        // 레이아웃 정보
        'pip_ratio': '${(_presetService.currentPreset.leftRatio * 100).round()}:${(_presetService.currentPreset.rightRatio * 100).round()}',
        'dock': '${AppDimens.dockWidth.toInt()}px',
        'content': '${(screenSize.width - AppDimens.dockWidth).toInt()}x${screenSize.height.toInt()}',
        
        // 상태
        'drawer': _showAppDrawer ? 'Open' : 'Closed',
        'fullscreen': _showFullscreenApp ? 'Yes' : 'No',
      };
    });
  }
  
  void _toggleDebugOverlay() {
    HapticFeedback.lightImpact();
    setState(() {
      _showDebugOverlay = !_showDebugOverlay;
    });
    if (_showDebugOverlay) {
      _updateDebugInfo();
    }
  }

  void _openAppDrawer() {
    // 앱 서랍을 오버레이로 표시 (Dock 오른쪽 영역에)
    HapticFeedback.lightImpact();
    setState(() {
      _showAppDrawer = true;
    });
    _drawerAnimController.forward();
  }
  
  void _closeAppDrawer() {
    HapticFeedback.lightImpact();
    _drawerAnimController.reverse().then((_) {
      if (mounted) {
        setState(() {
          _showAppDrawer = false;
        });
      }
    });
  }
  
  /// PIP 서랍 오버레이 열기 - 특정 PIP 영역에서 위로 슬라이드
  void _openPipDrawerOverlay(GlobalKey<PipViewState> pipKey) {
    HapticFeedback.lightImpact();
    setState(() {
      _pipDrawerTargetKey = pipKey;
      _showPipDrawerOverlay = true;
    });
    _pipDrawerAnimController.forward();
  }
  
  /// PIP 서랍 오버레이 닫기
  void _closePipDrawerOverlay() {
    HapticFeedback.lightImpact();
    _pipDrawerAnimController.reverse().then((_) {
      if (mounted) {
        setState(() {
          _showPipDrawerOverlay = false;
          _pipDrawerTargetKey = null;
        });
      }
    });
  }
  
  /// PIP 서랍에서 앱 선택 시 해당 PIP에 앱 실행
  void _launchAppInPip(String packageName) {
    if (_pipDrawerTargetKey?.currentState != null) {
      _pipDrawerTargetKey!.currentState!.launchAppWithConfig(packageName);
    }
    _closePipDrawerOverlay();
  }
  
  /// 앱 서랍에서 앱 선택 시 호출 - 전체화면 모드로 전환
  void _launchFullscreenApp(String packageName) {
    // CarrotPlay 설정 메뉴인 경우 프리셋 에디터 표시
    if (packageName == 'carrotplay.settings') {
      setState(() {
        _showAppDrawer = false;
      });
      _showPresetEditor(_presetService.selectedIndex);
      return;
    }
    
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
    
    // PIP 내 앱서랍 닫기
    _pip1Key.currentState?.closeInlineDrawer();
    _pip2Key.currentState?.closeInlineDrawer();
    
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
              Text('가로 모드로 전환 중...', 
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
        child: Stack(
          children: [
            // 메인 레이아웃
            Row(
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
                        
                      // 앱 서랍 오버레이 (애니메이션 적용)
                      if (_showAppDrawer)
                        Positioned.fill(
                          child: AnimatedBuilder(
                            animation: _drawerAnimController,
                            builder: (context, child) {
                              return Transform.translate(
                                offset: Offset(0, MediaQuery.of(context).size.height * _drawerSlideAnim.value),
                                child: Opacity(
                                  opacity: _drawerFadeAnim.value,
                                  child: child,
                                ),
                              );
                            },
                            child: AppDrawerContent(
                              onClose: _closeAppDrawer,
                              onAppSelected: _launchFullscreenApp,
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ],
            ),
            
            // 디버그 오버레이 (최상단, 터치 불가)
            if (_showDebugOverlay)
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: IgnorePointer(
                  child: _buildDebugOverlay(),
                ),
              ),
          ],
        ),
      ),
      ),
    );
  }
  
  /// 디버그 오버레이 위젯
  Widget _buildDebugOverlay() {
    if (_debugInfo.isEmpty) return const SizedBox.shrink();
    
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: Colors.black.withOpacity(0.6),
        border: Border(bottom: BorderSide(color: Colors.white.withOpacity(0.1))),
      ),
      child: DefaultTextStyle(
        style: const TextStyle(
          fontSize: 9,
          fontFamily: 'monospace',
          color: Colors.white,
          height: 1.3,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            // 1줄: 화면 정보
            Row(
              children: [
                _debugLabel('Screen', _debugInfo['screen']),
                _debugLabel('Physical', _debugInfo['physical']),
                _debugLabel('DPR', _debugInfo['dpr']),
                _debugLabel('DPI', '${_debugInfo['dpi']}'),
                _debugLabel('Orient', _debugInfo['orientation']),
              ],
            ),
            // 2줄: VirtualDisplay 정보
            Row(
              children: [
                _debugLabel('PIP1', 'VD${_debugInfo['pip1_vd']} [${_debugInfo['pip1_app']}]'),
                _debugLabel('PIP2', 'VD${_debugInfo['pip2_vd']} [${_debugInfo['pip2_app']}]'),
                _debugLabel('FS', 'VD${_debugInfo['fs_vd']} [${_debugInfo['fs_app']}]'),
              ],
            ),
            // 3줄: 레이아웃/상태 정보
            Row(
              children: [
                _debugLabel('Ratio', _debugInfo['pip_ratio']),
                _debugLabel('Dock', _debugInfo['dock']),
                _debugLabel('Content', _debugInfo['content']),
                _debugLabel('Drawer', _debugInfo['drawer']),
                _debugLabel('Fullscreen', _debugInfo['fullscreen']),
              ],
            ),
          ],
        ),
      ),
    );
  }
  
  Widget _debugLabel(String label, String? value) {
    return Padding(
      padding: const EdgeInsets.only(right: 12),
      child: RichText(
        text: TextSpan(
          style: const TextStyle(
            fontSize: 9,
            fontFamily: 'monospace',
            color: Colors.white70,
          ),
          children: [
            TextSpan(
              text: '$label:',
              style: TextStyle(color: AppColors.carrotOrange.withOpacity(0.8)),
            ),
            TextSpan(
              text: ' ${value ?? '-'}',
              style: const TextStyle(color: Colors.white),
            ),
          ],
        ),
      ),
    );
  }
  
  /// 분할 PIP 영역 (2개 PIP) - 현재 프리셋 비율 적용 + 드래그 가능한 구분선 + 하단 제스처 바
  Widget _buildSplitPipArea() {
    final preset = _presetService.currentPreset;
    final leftFlex = (preset.leftRatio * 100).round();
    final rightFlex = (preset.rightRatio * 100).round();
    
    return Container(
      color: AppColors.glassGrey,
      child: LayoutBuilder(
        builder: (context, constraints) {
          // PIP 제스처바 위치 계산 (마진 4px 고려)
          final leftWidth = constraints.maxWidth * preset.leftRatio - 4;
          final rightWidth = constraints.maxWidth * preset.rightRatio - 4;
          
          return Stack(
            children: [
              // PIP 영역들 (하단 제스처 바 영역 확보, 좌우 여백 동일하게)
              Padding(
                padding: const EdgeInsets.only(bottom: 20), // 하단 제스처바 영역
                child: Row(
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
                    // 구분선 공간 (제스처바 영역)
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
              ),
              
              // PIP 1 하단 제스처 바
              Positioned(
                left: 4, // PIP 마진과 동일
                width: leftWidth - 4, // 마진 보정
                bottom: 0,
                height: 20,
                child: _PipGestureBar(
                  pipKey: _pip1Key,
                  onFullscreen: () => _launchFullscreenFromPip(_pip1Key),
                ),
              ),
              
              // PIP 2 하단 제스처 바
              Positioned(
                right: 4, // PIP 마진과 동일
                width: rightWidth - 4, // 마진 보정
                bottom: 0,
                height: 20,
                child: _PipGestureBar(
                  pipKey: _pip2Key,
                  onFullscreen: () => _launchFullscreenFromPip(_pip2Key),
                ),
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
                  onRatioChangeEnd: (finalRatio) {
                    // 비율 변경 완료 시 VirtualDisplay 크기 재조정
                    _resizePipDisplays(finalRatio);
                  },
                ),
              ),
              

            ],
          );
        },
      ),
    );
  }
  
  /// PIP VirtualDisplay 크기 재조정
  void _resizePipDisplays(double leftRatio) {
    final mediaQuery = MediaQuery.of(context);
    final screenSize = mediaQuery.size;
    final devicePixelRatio = mediaQuery.devicePixelRatio;
    final contentWidth = screenSize.width - AppDimens.dockWidth;
    
    // PIP 1 크기 재조정
    _pip1Key.currentState?.resizeToFit(
      leftRatio,
      contentWidth,
      screenSize.height,
      devicePixelRatio,
    );
    
    // PIP 2 크기 재조정
    _pip2Key.currentState?.resizeToFit(
      1.0 - leftRatio,
      contentWidth,
      screenSize.height,
      devicePixelRatio,
    );
  }
  
  /// PIP에서 전체화면으로 전환
  void _launchFullscreenFromPip(GlobalKey<PipViewState> pipKey) {
    final currentPackage = pipKey.currentState?.currentPackage;
    if (currentPackage != null) {
      _launchFullscreenApp(currentPackage);
    }
  }
  
  /// PIP 앱서랍 오버레이 빌드 - 하단에서 위로 올라오는 애니메이션
  Widget _buildPipDrawerOverlay(BoxConstraints constraints) {
    // 타겟 PIP 위치 계산
    final preset = _presetService.currentPreset;
    final isLeftPip = _pipDrawerTargetKey == _pip1Key;
    final pipWidth = isLeftPip 
        ? constraints.maxWidth * preset.leftRatio - 4
        : constraints.maxWidth * preset.rightRatio - 4;
    final pipLeft = isLeftPip ? 0.0 : constraints.maxWidth * preset.leftRatio + 4;
    
    return AnimatedBuilder(
      animation: _pipDrawerSlideAnim,
      builder: (context, child) {
        return Positioned(
          left: pipLeft,
          right: isLeftPip ? constraints.maxWidth - pipWidth : 0,
          bottom: 20 + (_pipDrawerSlideAnim.value * constraints.maxHeight),
          height: constraints.maxHeight - 20,
          child: GestureDetector(
            onTap: () {}, // 터치 이벤트 소비
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                child: Container(
                  decoration: BoxDecoration(
                    color: AppColors.glassGrey.withOpacity(0.95),
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.1),
                      width: 1,
                    ),
                  ),
                  child: Column(
                    children: [
                      // 상단 핸들
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        child: GestureDetector(
                          onTap: _closePipDrawerOverlay,
                          child: Container(
                            width: 36,
                            height: 4,
                            decoration: BoxDecoration(
                              color: Colors.white24,
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ),
                      ),
                      // 앱 그리드
                      Expanded(
                        child: AppDrawerContent(
                          onAppSelected: _launchAppInPip,
                          showCarrotPlaySettings: false,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  /// Dock (왼쪽 네비게이션 바) - 반응형 레이아웃
  Widget _buildDock() {
    return ClipRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
        child: Container(
          width: AppDimens.dockWidth,
          decoration: BoxDecoration(
            color: AppColors.glassGrey.withOpacity(0.8),
            border: const Border(
              right: BorderSide(color: Colors.white10, width: 1),
            ),
          ),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final screenHeight = constraints.maxHeight;
              final isCompact = screenHeight < 500; // SafeArea 적용 후 ~411px
              final isUltraCompact = screenHeight < 300;
              
              // 화면 높이별 프리셋 개수
              final maxPresets = _getMaxPresets(screenHeight);
              // 화면 높이별 버튼 크기
              final presetSize = _getPresetButtonSize(screenHeight);
              // 화면 높이별 폰트 크기
              final clockFontSize = isCompact ? 16.0 : 20.0;
              final networkIconSize = isCompact ? 14.0 : 16.0;
              final networkFontSize = isCompact ? 10.0 : 11.0;
              final appButtonSize = isCompact ? 44.0 : 52.0;
              
              // 실제 표시할 프리셋 개수
              final presetsToShow = maxPresets.clamp(0, _presetService.presets.length);
              
              // 디버그: 화면 높이와 계산된 값 출력
              debugPrint('Dock: screenHeight=$screenHeight, isCompact=$isCompact, maxPresets=$maxPresets, presetsToShow=$presetsToShow');
              
              return Column(
                children: [
                  SizedBox(height: isCompact ? 8 : AppDimens.paddingMedium),
                  
                  // 상단: 시계 (터치하면 디버그 토글)
                  GestureDetector(
                    onTap: _toggleDebugOverlay,
                    behavior: HitTestBehavior.opaque,
                    child: Text(
                      _currentTime,
                      style: TextStyle(
                        fontSize: clockFontSize,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                        letterSpacing: 1.0,
                      ),
                    ),
                  ),
                  
                  SizedBox(height: isCompact ? 4 : AppDimens.paddingSmall),
                  
                  // 네트워크 상태 (컴팩트 모드에서 축소)
                  if (!isUltraCompact)
                    StreamBuilder<NetworkStatus>(
                      stream: _connectivity.statusStream,
                      initialData: _connectivity.currentStatus,
                      builder: (context, snapshot) {
                        final status = snapshot.data!;
                        return Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  status.isWifi ? Icons.wifi_rounded : Icons.signal_cellular_alt_rounded,
                                  size: networkIconSize,
                                  color: status.isConnected ? AppColors.carrotOrange : Colors.white30,
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  status.isWifi ? 'WiFi' : 'LTE',
                                  style: TextStyle(
                                    fontSize: networkFontSize,
                                    fontWeight: FontWeight.w500,
                                    color: status.isConnected ? Colors.white70 : Colors.white38,
                                  ),
                                ),
                              ],
                            ),
                            if (status.networkName.isNotEmpty) ...[
                              const SizedBox(height: 2),
                              SizedBox(
                                width: 60,
                                child: Text(
                                  status.networkName,
                                  style: const TextStyle(
                                    fontSize: 9,
                                    color: Colors.white38,
                                  ),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            ],
                          ],
                        );
                      },
                    ),
                  
                  // 정중앙: 프리셋 버튼들 (반응형 개수)
                  Expanded(
                    child: Center(
                      child: SingleChildScrollView(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            for (int i = 0; i < presetsToShow; i++) ...[
                              if (i > 0) SizedBox(height: isCompact ? 6 : 8),
                              _buildPresetButton(i, presetSize),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                  
                  // 하단: 앱서랍 버튼 또는 닫기 버튼
                  Padding(
                    padding: EdgeInsets.only(bottom: isCompact ? 10 : 16),
                    child: BouncyButton(
                      onPressed: () {
                        if (_showFullscreenApp) {
                          _closeFullscreenApp();
                        } else if (_showAppDrawer) {
                          _closeAppDrawer();
                        } else {
                          _openAppDrawer();
                        }
                      },
                      child: Container(
                        width: appButtonSize,
                        height: appButtonSize,
                        decoration: BoxDecoration(
                          color: (_showAppDrawer || _showFullscreenApp)
                              ? AppColors.carrotOrange.withOpacity(0.3)
                              : Colors.white.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
                          border: (_showAppDrawer || _showFullscreenApp)
                              ? Border.all(color: AppColors.carrotOrange, width: 2)
                              : null,
                        ),
                        child: Icon(
                          _showFullscreenApp 
                              ? Icons.close 
                              : (_showAppDrawer ? Icons.close : Icons.apps),
                          color: Colors.white,
                          size: isCompact ? 24 : 28,
                        ),
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
  
  /// 화면 높이별 최대 프리셋 개수
  int _getMaxPresets(double screenHeight) {
    if (screenHeight >= 600) return 5;  // 태블릿/일반 모니터
    if (screenHeight >= 450) return 4;  // 일반 가로 모드
    if (screenHeight >= 350) return 3;  // 차량 디스플레이 (SafeArea 후 ~400px)
    if (screenHeight >= 250) return 2;  // 매우 좁은 화면
    return 1;
  }
  
  /// 화면 높이별 프리셋 버튼 크기
  double _getPresetButtonSize(double screenHeight) {
    if (screenHeight >= 600) return 56.0;
    if (screenHeight >= 450) return 48.0;
    if (screenHeight >= 350) return 44.0;
    return 40.0;
  }

  Widget _buildPresetButton(int index, [double size = 56]) {
    final isSelected = _presetService.selectedIndex == index;
    final preset = _presetService.presets[index];
    
    return BouncyButton(
      onPressed: () => _onPresetTap(index),
      onLongPress: () => _onPresetLongPress(index),
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: isSelected 
              ? AppColors.carrotOrange.withOpacity(0.3) 
              : Colors.white.withOpacity(0.05),
          borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
          border: isSelected 
              ? Border.all(color: AppColors.carrotOrange, width: 2)
              : Border.all(color: Colors.white24, width: 1),
        ),
        child: _buildRatioIndicator(preset, isSelected, size),
      ),
    );
  }

  /// 원본 앱처럼 좌우 비율을 시각적으로 표시하는 위젯
  Widget _buildRatioIndicator(PresetConfig preset, bool isSelected, [double size = 56]) {
    final leftRatio = preset.leftRatio;
    final rightRatio = preset.rightRatio;
    
    // 비율 라벨 (예: "50:50")
    final leftPercent = (leftRatio * 100).round();
    final rightPercent = (rightRatio * 100).round();
    
    // 크기에 따른 패딩 조절
    final padding = size < 48 ? 3.0 : 5.0;
    final borderRadius = size < 48 ? 2.0 : 3.0;
    
    return Padding(
      padding: EdgeInsets.all(padding),
      child: Column(
        children: [
          // 비율 막대 (전체 영역)
          Expanded(
            child: Row(
              children: [
                // 왼쪽 영역 (pip1) - 50% 고정
                Expanded(
                  flex: 1,
                  child: Container(
                    margin: const EdgeInsets.only(right: 1),
                    decoration: BoxDecoration(
                      color: preset.pip1.isNotEmpty 
                          ? AppColors.carrotOrange.withOpacity(0.5)
                          : Colors.white.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(borderRadius),
                    ),
                    child: preset.pip1.icon != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(borderRadius),
                            child: Image.memory(preset.pip1.icon!, fit: BoxFit.cover),
                          )
                        : null,
                  ),
                ),
                // 오른쪽 영역 (pip2) - 50% 고정
                Expanded(
                  flex: 1,
                  child: Container(
                    margin: const EdgeInsets.only(left: 1),
                    decoration: BoxDecoration(
                      color: preset.pip2.isNotEmpty 
                          ? AppColors.successGreen.withOpacity(0.5)
                          : Colors.white.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(borderRadius),
                    ),
                    child: preset.pip2.icon != null
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(borderRadius),
                            child: Image.memory(preset.pip2.icon!, fit: BoxFit.cover),
                          )
                        : null,
                  ),
                ),
              ],
            ),
          ),
          // 비율 텍스트 (하단 고정 높이)
          Container(
            height: 12,
            alignment: Alignment.center,
            child: Text(
              '$leftPercent:$rightPercent',
              style: TextStyle(
                fontSize: 7,
                fontWeight: FontWeight.w600,
                color: isSelected ? Colors.white : Colors.white70,
              ),
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
  final ValueChanged<double>? onRatioChangeEnd;

  const _RatioResizer({
    required this.maxWidth,
    required this.currentRatio,
    required this.onRatioChanged,
    this.onRatioChangeEnd,
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
        if (_isDragEnabled) {
          // 드래그가 끝날 때 최종 비율 콜백
          widget.onRatioChangeEnd?.call(widget.currentRatio);
        }
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
              height: _isDragEnabled ? 120 : 80, // PIP 제스처바와 동일하게
              decoration: BoxDecoration(
                color: _isDragEnabled 
                    ? AppColors.carrotOrange 
                    : (_isHovering ? Colors.white38 : Colors.white24),
                borderRadius: BorderRadius.circular(3),
                boxShadow: _isDragEnabled
                    ? [
                        BoxShadow(
                          color: AppColors.carrotOrange.withOpacity(0.5),
                          blurRadius: 8,
                          spreadRadius: 1,
                        )
                      ]
                    : [],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// PIP 하단 제스처 바 (좌우비율 제스처바와 동일한 디자인)
/// - 위로 슬라이드: PIP 내 앱서랍 열기
/// - 2초 롱프레스: 전체화면 전환
class _PipGestureBar extends StatefulWidget {
  final GlobalKey<PipViewState> pipKey;
  final VoidCallback onFullscreen;

  const _PipGestureBar({
    required this.pipKey,
    required this.onFullscreen,
  });

  @override
  State<_PipGestureBar> createState() => _PipGestureBarState();
}

class _PipGestureBarState extends State<_PipGestureBar> {
  bool _isActive = false;
  bool _isHovering = false;
  Timer? _longPressTimer;
  double _dragOffsetX = 0;
  bool _isDragging = false;
  bool _backTriggered = false;

  @override
  void dispose() {
    _longPressTimer?.cancel();
    super.dispose();
  }

  void _resetState() {
    _longPressTimer?.cancel();
    _isDragging = false;
    _dragOffsetX = 0;
    _isActive = false;
    _backTriggered = false;
  }

  void _onPanStart(DragStartDetails details) {
    _resetState();
    _isDragging = true;
    _startLongPressTimer();
    setState(() {});
  }

  void _onPanUpdate(DragUpdateDetails details) {
    if (!_isDragging) return;
    
    // 인터랙티브하게 오프셋 업데이트
    setState(() {
      _dragOffsetX = details.delta.dx + _dragOffsetX;
      _dragOffsetX = _dragOffsetX.clamp(-60.0, 60.0);
    });
    
    // 좌측으로 50px 이상 드래그하면 뒤로가기 (한 번만)
    if (_dragOffsetX < -50 && !_backTriggered) {
      _longPressTimer?.cancel();
      _backTriggered = true;
      _goBack();
    }
  }

  void _onPanEnd(DragEndDetails details) {
    if (!_isDragging) return;
    
    final wasActive = _isActive;
    final velocity = details.velocity.pixelsPerSecond.dx;
    
    // 상태 즉시 초기화
    setState(() {
      _resetState();
    });
    
    // 롱프레스 완료 시 전체화면
    if (wasActive) {
      widget.onFullscreen();
    }
    // 빠른 좌측 스와이프로 뒤로가기
    else if (velocity < -300 && !_backTriggered) {
      _goBack();
    }
  }
  
  void _onPanCancel() {
    setState(() {
      _resetState();
    });
  }

  void _startLongPressTimer() {
    _longPressTimer?.cancel();
    _longPressTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted && _isDragging) {
        setState(() => _isActive = true);
        HapticFeedback.heavyImpact();
      }
    });
  }

  void _goBack() {
    HapticFeedback.mediumImpact();
    // 해당 PIP에 뒤로가기 명령 전송 (fire-and-forget)
    widget.pipKey.currentState?.sendBackKey();
  }

  @override
  Widget build(BuildContext context) {
    // 드래그 진행률 계산 (0~1, 뒤로가기 방향)
    final dragProgress = (_dragOffsetX.abs() / 50).clamp(0.0, 1.0);
    final isBackDirection = _dragOffsetX < 0;
    
    return GestureDetector(
      onPanStart: _onPanStart,
      onPanUpdate: _onPanUpdate,
      onPanEnd: _onPanEnd,
      onPanCancel: _onPanCancel,
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovering = true),
        onExit: (_) => setState(() => _isHovering = false),
        child: Container(
          color: Colors.transparent,
          child: Stack(
            alignment: Alignment.center,
            children: [
              // 뒤로가기 방향 표시 (좌측 화살표)
              if (_isDragging && isBackDirection && dragProgress > 0.2)
                Positioned(
                  left: 8,
                  child: Opacity(
                    opacity: dragProgress,
                    child: Icon(
                      Icons.arrow_back_ios_rounded,
                      color: AppColors.carrotOrange,
                      size: 16,
                    ),
                  ),
                ),
              // 메인 제스처 바 (즉각 반응)
              Transform.translate(
                offset: Offset(_dragOffsetX * 0.5, 0),
                child: Container(
                  width: _isActive ? 100 : 80,
                  height: _isActive ? 5 : 4,
                  decoration: BoxDecoration(
                    color: _isActive 
                        ? AppColors.carrotOrange 
                        : (_isDragging && isBackDirection && dragProgress > 0.5)
                            ? AppColors.carrotOrange.withOpacity(0.7)
                            : (_isHovering ? Colors.white38 : Colors.white24),
                    borderRadius: BorderRadius.circular(3),
                    boxShadow: _isActive
                        ? [
                            BoxShadow(
                              color: AppColors.carrotOrange.withOpacity(0.5),
                              blurRadius: 8,
                              spreadRadius: 1,
                            )
                          ]
                        : [],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
