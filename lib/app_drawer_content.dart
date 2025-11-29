import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'theme/app_colors.dart';
import 'theme/app_text_styles.dart';
import 'theme/app_dimens.dart';
import 'widgets/common/app_icon_wrapper.dart';
import 'widgets/animations/bouncy_button.dart';

/// App Information Data Class
class AppInfo {
  final String packageName;
  final String appName;
  final Uint8List? icon;

  AppInfo({
    required this.packageName,
    required this.appName,
    this.icon,
  });
}

/// Global App Cache - Preloads installed apps
class AppCache {
  static final AppCache _instance = AppCache._internal();
  factory AppCache() => _instance;
  AppCache._internal();
  
  static const _channel = MethodChannel('com.carcarlauncher.clone/launcher');
  
  List<AppInfo>? _apps;
  bool _isLoading = false;
  bool _isLoaded = false;
  
  List<AppInfo> get apps => _apps ?? [];
  bool get isLoaded => _isLoaded;
  bool get isLoading => _isLoading;
  
  static Future<void> preload() async {
    await _instance._load();
  }
  
  Future<void> _load() async {
    if (_isLoaded || _isLoading) return;
    _isLoading = true;
    
    try {
      final result = await _channel.invokeMethod('getInstalledApps');
      if (result != null) {
        final List<dynamic> appList = result;
        _apps = appList.map((app) => AppInfo(
          packageName: app['packageName'] ?? '',
          appName: app['appName'] ?? '',
          icon: app['icon'] != null 
              ? Uint8List.fromList(List<int>.from(app['icon'])) 
              : null,
        )).toList();
        _apps!.sort((a, b) => 
            a.appName.toLowerCase().compareTo(b.appName.toLowerCase()));
        _isLoaded = true;
      }
    } catch (e) {
      debugPrint('AppCache load failed: $e');
    }
    
    _isLoading = false;
  }
  
  Future<void> refresh() async {
    _isLoaded = false;
    _apps = null;
    await _load();
  }
}

/// App Drawer Content - Redesigned
class AppDrawerContent extends StatefulWidget {
  final VoidCallback? onClose;
  final void Function(String packageName)? onAppSelected;
  final bool showCarrotPlaySettings;
  
  const AppDrawerContent({
    super.key, 
    this.onClose,
    this.onAppSelected,
    this.showCarrotPlaySettings = true,
  });

  @override
  State<AppDrawerContent> createState() => _AppDrawerContentState();
}

class _AppDrawerContentState extends State<AppDrawerContent> {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  
  // Grid Configuration (2 rows, 5 columns)
  static const int _colCount = 5;
  static const int _rowCount = 2;
  static const int _itemsPerPage = _colCount * _rowCount;

  // CarrotPlay 앱은 Native에서 이미 필터링됨
  // 여기서는 추가 필터링 없이 그대로 사용
  List<AppInfo> get _filteredApps => AppCache().apps;
  
  // 첫 칸에 CarrotPlay 설정 추가 (옵션에 따라)
  List<AppInfo> get _apps {
    final apps = _filteredApps;
    if (widget.showCarrotPlaySettings) {
      // 첫 칸에 CarrotPlay 설정 메뉴 추가
      final settingsItem = AppInfo(
        packageName: 'carrotplay.settings',
        appName: 'CarrotPlay',
        icon: null, // 커스텀 아이콘 사용
      );
      return [settingsItem, ...apps];
    }
    return apps;
  }
  
  int get _totalPages => (_apps.length / _itemsPerPage).ceil().clamp(1, 100);

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _onAppTap(String packageName) {
    if (widget.onAppSelected != null) {
      widget.onAppSelected!(packageName);
    } else {
      widget.onClose?.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_apps.isEmpty) {
      return Container(
        color: AppColors.glassGrey,
        child: const Center(
          child: CircularProgressIndicator(color: Colors.white38),
        ),
      );
    }
    
    return Container(
      color: AppColors.glassGrey,
      child: Column(
        children: [
          // App Grid
          Expanded(
            child: PageView.builder(
            controller: _pageController,
            onPageChanged: (page) => setState(() => _currentPage = page),
            itemCount: _totalPages,
            itemBuilder: (context, pageIndex) => _buildAppGrid(pageIndex),
          ),
        ),
          // Page Indicator
          _buildPageIndicator(),
        ],
      ),
    );
  }

  Widget _buildAppGrid(int pageIndex) {
    final startIndex = pageIndex * _itemsPerPage;
    final endIndex = (startIndex + _itemsPerPage).clamp(0, _apps.length);
    final pageApps = _apps.sublist(startIndex, endIndex);

    return LayoutBuilder(
      builder: (context, constraints) {
        final availableWidth = constraints.maxWidth;
        final availableHeight = constraints.maxHeight;
        const spacing = AppDimens.paddingMedium;
        
        final itemWidth = (availableWidth - spacing * (_colCount + 1)) / _colCount;
        final itemHeight = (availableHeight - spacing * (_rowCount + 1)) / _rowCount;
        final itemSize = itemWidth < itemHeight ? itemWidth : itemHeight;
        
        final gridWidth = itemSize * _colCount + spacing * (_colCount - 1);
        final gridHeight = itemSize * _rowCount + spacing * (_rowCount - 1);
        
        final horizontalPadding = (availableWidth - gridWidth) / 2;
        final verticalPadding = (availableHeight - gridHeight) / 2;
        
        return Padding(
          padding: EdgeInsets.symmetric(
            horizontal: horizontalPadding.clamp(spacing, double.infinity),
            vertical: verticalPadding.clamp(spacing, double.infinity),
          ),
          child: GridView.builder(
            physics: const NeverScrollableScrollPhysics(),
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: _colCount,
              childAspectRatio: 1.0,
              crossAxisSpacing: spacing,
              mainAxisSpacing: spacing,
            ),
            itemCount: pageApps.length,
            itemBuilder: (context, index) => _buildAppItem(pageApps[index]),
          ),
        );
      },
    );
  }

  Widget _buildAppItem(AppInfo app) {
    // CarrotPlay 설정 메뉴 (첫 칸)
    if (app.packageName == 'carrotplay.settings') {
      return BouncyButton(
        onPressed: () => _openCarrotPlaySettings(),
        child: Center(
          child: FractionallySizedBox(
            widthFactor: 0.8,
            heightFactor: 0.8,
            child: Container(
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFFFF8F40), Color(0xFFFF6B00)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.carrotOrange.withOpacity(0.3),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: const Icon(
                Icons.settings_rounded,
                color: Colors.white,
                size: 36,
              ),
            ),
          ),
        ),
      );
    }
    
    // 일반 앱 아이콘 (이름 없음), 0.8 스케일 유지
    return BouncyButton(
      onPressed: () => _onAppTap(app.packageName),
      child: Center(
        child: FractionallySizedBox(
          widthFactor: 0.8,
          heightFactor: 0.8,
          child: app.icon != null
              ? ClipRRect(
                  borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
                  child: Image.memory(
                    app.icon!,
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  ),
                )
              : Container(
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
                  ),
                  child: Icon(
                    _getAppIcon(app.packageName),
                    color: Colors.white54,
                    size: 32,
                  ),
                ),
        ),
      ),
    );
  }

  void _openCarrotPlaySettings() {
    // 설정 메뉴를 콜백으로 전달 (onAppSelected에 특수 패키지명 전달)
    // onClose보다 먼저 호출해야 위젯이 살아있을 때 콜백 전달됨
    if (widget.onAppSelected != null) {
      widget.onAppSelected!('carrotplay.settings');
    } else {
      widget.onClose?.call();
    }
  }

  IconData _getAppIcon(String packageName) {
    if (packageName.contains('youtube')) return Icons.play_circle;
    if (packageName.contains('netflix')) return Icons.movie;
    if (packageName.contains('spotify') || packageName.contains('music')) return Icons.music_note;
    if (packageName.contains('map') || packageName.contains('navi')) return Icons.map;
    if (packageName.contains('chrome') || packageName.contains('browser')) return Icons.public;
    if (packageName.contains('setting')) return Icons.settings;
    return Icons.android;
  }

  Widget _buildPageIndicator() {
    if (_totalPages <= 1) return const SizedBox(height: 24);
    
    return Container(
      height: 24,
      padding: const EdgeInsets.only(bottom: AppDimens.paddingSmall),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(_totalPages, (index) {
          final isActive = index == _currentPage;
          return GestureDetector(
            onTap: () => _pageController.animateToPage(
              index,
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeOutQuart,
            ),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              width: isActive ? 16 : 6,
              height: 6,
              margin: const EdgeInsets.symmetric(horizontal: 3),
              decoration: BoxDecoration(
                color: isActive ? AppColors.carrotOrange : Colors.white30,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          );
        }),
      ),
    );
  }
}
