import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 앱 정보 클래스
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

/// 글로벌 앱 캐시 - 앱 시작시 미리 로드
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
  
  /// 앱 시작시 호출 - 미리 로드
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
  
  /// 강제 새로고침
  Future<void> refresh() async {
    _isLoaded = false;
    _apps = null;
    await _load();
  }
}

/// 앱 서랍 콘텐츠 - 완전히 새로 설계
class AppDrawerContent extends StatefulWidget {
  final VoidCallback onClose;
  final void Function(String packageName)? onAppSelected;
  
  const AppDrawerContent({
    super.key, 
    required this.onClose,
    this.onAppSelected,
  });

  @override
  State<AppDrawerContent> createState() => _AppDrawerContentState();
}

class _AppDrawerContentState extends State<AppDrawerContent> {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  
  // 그리드 설정 (2행 5열)
  static const int _colCount = 5;
  static const int _rowCount = 2;
  static const int _itemsPerPage = _colCount * _rowCount;

  List<AppInfo> get _apps => AppCache().apps;
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
      widget.onClose();
    }
  }

  @override
  Widget build(BuildContext context) {
    // 캐시가 없으면 빈 화면 (거의 발생 안함)
    if (_apps.isEmpty) {
      return Container(
        color: const Color(0xFF1A1A1A),
        child: const Center(
          child: CircularProgressIndicator(color: Colors.white38),
        ),
      );
    }
    
    return Container(
      color: const Color(0xFF1A1A1A),
      child: Column(
        children: [
          // 앱 그리드
          Expanded(
            child: PageView.builder(
              controller: _pageController,
              onPageChanged: (page) => setState(() => _currentPage = page),
              itemCount: _totalPages,
              itemBuilder: (context, pageIndex) => _buildAppGrid(pageIndex),
            ),
          ),
          // 페이지 인디케이터
          _buildPageIndicator(),
        ],
      ),
    );
  }

  Widget _buildAppGrid(int pageIndex) {
    final startIndex = pageIndex * _itemsPerPage;
    final endIndex = (startIndex + _itemsPerPage).clamp(0, _apps.length);
    final pageApps = _apps.sublist(startIndex, endIndex);

    // CarPlay 스타일: 상하좌우 동일 여백
    return LayoutBuilder(
      builder: (context, constraints) {
        // 아이템 크기 계산 (2행 5열)
        final availableWidth = constraints.maxWidth;
        final availableHeight = constraints.maxHeight;
        final spacing = 12.0;
        
        // 가로 기준으로 아이템 크기 계산
        final itemWidth = (availableWidth - spacing * (_colCount + 1)) / _colCount;
        final itemHeight = (availableHeight - spacing * (_rowCount + 1)) / _rowCount;
        final itemSize = itemWidth < itemHeight ? itemWidth : itemHeight;
        
        // 전체 그리드 크기
        final gridWidth = itemSize * _colCount + spacing * (_colCount - 1);
        final gridHeight = itemSize * _rowCount + spacing * (_rowCount - 1);
        
        // 중앙 정렬을 위한 패딩
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
    // 아이콘 크기 20% 줄임 (0.8 스케일)
    return GestureDetector(
      onTap: () => _onAppTap(app.packageName),
      behavior: HitTestBehavior.opaque,
      child: Center(
        child: FractionallySizedBox(
          widthFactor: 0.8,
          heightFactor: 0.8,
          child: app.icon != null
              ? ClipRRect(
                  borderRadius: BorderRadius.circular(14),
                  child: Image.memory(
                    app.icon!,
                    fit: BoxFit.cover,
                    gaplessPlayback: true,
                  ),
                )
              : Container(
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(14),
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

  Widget _buildPageIndicator() {
    if (_totalPages <= 1) return const SizedBox(height: 24);
    
    return Container(
      height: 24,
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: List.generate(_totalPages, (index) {
          final isActive = index == _currentPage;
          return GestureDetector(
            onTap: () => _pageController.animateToPage(
              index,
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeOut,
            ),
            child: Container(
              width: isActive ? 16 : 6,
              height: 6,
              margin: const EdgeInsets.symmetric(horizontal: 3),
              decoration: BoxDecoration(
                color: isActive ? Colors.white : Colors.white30,
                borderRadius: BorderRadius.circular(3),
              ),
            ),
          );
        }),
      ),
    );
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
}
