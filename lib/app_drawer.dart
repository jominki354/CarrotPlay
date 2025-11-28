import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 앱 서랍 - 전체화면 페이지 (2행 5열 그리드, 페이지 스크롤)
/// 원본 앱과 동일한 구조: 왼쪽 Dock + 오른쪽 앱 그리드
class AppDrawer extends StatefulWidget {
  /// 앱을 실행할 대상 VirtualDisplay ID
  /// null이면 기본 동작 (첫 번째 PIP에서 실행)
  final int? targetDisplayId;
  
  const AppDrawer({super.key, this.targetDisplayId});

  @override
  State<AppDrawer> createState() => _AppDrawerState();
}

class _AppDrawerState extends State<AppDrawer> {
  static const _channel = MethodChannel('com.carcarlauncher.clone/launcher');
  List<AppInfo> _apps = [];
  bool _isLoading = true;
  
  // 페이지 관련
  final PageController _pageController = PageController();
  int _currentPage = 0;
  
  // 그리드 설정 (2행 5열 = 페이지당 10개)
  static const int _rowCount = 2;
  static const int _colCount = 5;
  static const int _itemsPerPage = _rowCount * _colCount;

  @override
  void initState() {
    super.initState();
    _loadApps();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  int get _totalPages => (_apps.length / _itemsPerPage).ceil().clamp(1, 100);

  Future<void> _loadApps() async {
    try {
      final result = await _channel.invokeMethod('getInstalledApps');
      if (result != null) {
        final List<dynamic> appList = result;
        setState(() {
          _apps = appList.map((app) => AppInfo(
            packageName: app['packageName'] ?? '',
            appName: app['appName'] ?? '',
            icon: app['icon'] != null ? Uint8List.fromList(List<int>.from(app['icon'])) : null,
          )).toList();
          _apps.sort((a, b) => a.appName.toLowerCase().compareTo(b.appName.toLowerCase()));
          _isLoading = false;
        });
      }
    } catch (e) {
      debugPrint('Failed to load apps: $e');
      // 테스트용 더미 데이터
      setState(() {
        _apps = List.generate(25, (i) => AppInfo(
          packageName: 'com.test.app$i',
          appName: 'Test App ${i + 1}',
        ));
        _isLoading = false;
      });
    }
  }

  /// VirtualDisplay에 전체화면으로 앱 실행 (원본 앱 방식)
  /// targetDisplayId가 지정되면 해당 디스플레이에서 실행
  Future<void> _launchAppOnDisplay(String packageName) async {
    final targetId = widget.targetDisplayId;
    
    try {
      if (targetId != null) {
        // 지정된 VirtualDisplay에서 실행 (원본 앱 z7/m.java n() 방식)
        debugPrint('Launching $packageName on VirtualDisplay $targetId');
        await _channel.invokeMethod('launchApp', {
          'packageName': packageName,
          'displayId': targetId,
        });
      } else {
        // displayId가 없으면 기본 launchApp 호출 (displayId 0 = 첫 번째 PIP)
        debugPrint('Launching $packageName on default display');
        await _channel.invokeMethod('launchApp', {
          'packageName': packageName,
          'displayId': 0,
        });
      }
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      debugPrint('Failed to launch app: $e');
    }
  }

  void _goToPreviousPage() {
    if (_currentPage > 0) {
      _pageController.previousPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  void _goToNextPage() {
    if (_currentPage < _totalPages - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1A1A1A),
      body: Row(
        children: [
          // 왼쪽 Dock (원본 앱과 동일)
          _buildDock(),
          
          // 오른쪽 앱 그리드 영역
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : Column(
                    children: [
                      // 앱 그리드 (PageView)
                      Expanded(
                        child: PageView.builder(
                          controller: _pageController,
                          onPageChanged: (page) {
                            setState(() => _currentPage = page);
                          },
                          itemCount: _totalPages,
                          itemBuilder: (context, pageIndex) {
                            return _buildAppGrid(pageIndex);
                          },
                        ),
                      ),
                      
                      // 하단 네비게이션
                      _buildPageNavigation(),
                    ],
                  ),
          ),
        ],
      ),
    );
  }

  /// 왼쪽 Dock (홈버튼, 시계 등)
  Widget _buildDock() {
    return Container(
      width: 80,
      decoration: const BoxDecoration(
        color: Color(0xFF252525),
        border: Border(
          right: BorderSide(color: Colors.white10, width: 1),
        ),
      ),
      child: Column(
        children: [
          const SizedBox(height: 20),
          
          // 닫기 버튼 (뒤로가기)
          _buildDockButton(
            icon: Icons.close,
            label: '닫기',
            onTap: () => Navigator.of(context).pop(),
          ),
          
          const Spacer(),
          
          // 현재 시간
          StreamBuilder(
            stream: Stream.periodic(const Duration(seconds: 1)),
            builder: (context, snapshot) {
              final now = DateTime.now();
              return Column(
                children: [
                  Text(
                    '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    '${now.month}/${now.day}',
                    style: const TextStyle(
                      color: Colors.white54,
                      fontSize: 12,
                    ),
                  ),
                ],
              );
            },
          ),
          
          const SizedBox(height: 20),
        ],
      ),
    );
  }

  Widget _buildDockButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 60,
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: Column(
          children: [
            Icon(icon, color: Colors.white70, size: 24),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(color: Colors.white54, fontSize: 10),
            ),
          ],
        ),
      ),
    );
  }

  /// 앱 그리드 (2행 5열)
  Widget _buildAppGrid(int pageIndex) {
    final startIndex = pageIndex * _itemsPerPage;
    final endIndex = (startIndex + _itemsPerPage).clamp(0, _apps.length);
    final pageApps = _apps.sublist(startIndex, endIndex);

    return Padding(
      padding: const EdgeInsets.all(24),
      child: GridView.builder(
        physics: const NeverScrollableScrollPhysics(),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: _colCount,
          childAspectRatio: 0.85,
          crossAxisSpacing: 16,
          mainAxisSpacing: 16,
        ),
        itemCount: pageApps.length,
        itemBuilder: (context, index) {
          return _buildAppItem(pageApps[index]);
        },
      ),
    );
  }

  Widget _buildAppItem(AppInfo app) {
    return InkWell(
      onTap: () => _launchAppOnDisplay(app.packageName),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(8),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 앱 아이콘
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.1),
                borderRadius: BorderRadius.circular(16),
              ),
              child: app.icon != null
                  ? ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: Image.memory(
                        app.icon!,
                        fit: BoxFit.cover,
                        errorBuilder: (_, __, ___) => _buildDefaultIcon(app.packageName),
                      ),
                    )
                  : _buildDefaultIcon(app.packageName),
            ),
            const SizedBox(height: 8),
            // 앱 이름
            Text(
              app.appName,
              style: const TextStyle(
                fontSize: 12,
                color: Colors.white,
              ),
              maxLines: 2,
              textAlign: TextAlign.center,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDefaultIcon(String packageName) {
    return Icon(
      _getAppIcon(packageName),
      color: Colors.white54,
      size: 32,
    );
  }

  /// 하단 페이지 네비게이션
  Widget _buildPageNavigation() {
    return Container(
      height: 60,
      padding: const EdgeInsets.symmetric(horizontal: 24),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // 이전 페이지 버튼
          IconButton(
            onPressed: _currentPage > 0 ? _goToPreviousPage : null,
            icon: Icon(
              Icons.chevron_left,
              color: _currentPage > 0 ? Colors.white : Colors.white24,
              size: 32,
            ),
          ),
          
          const SizedBox(width: 16),
          
          // 페이지 인디케이터 (중앙 정렬)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: List.generate(_totalPages, (index) {
              final isActive = index == _currentPage;
              return GestureDetector(
                onTap: () {
                  _pageController.animateToPage(
                    index,
                    duration: const Duration(milliseconds: 300),
                    curve: Curves.easeInOut,
                  );
                },
                child: Container(
                  width: isActive ? 24 : 8,
                  height: 8,
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  decoration: BoxDecoration(
                    color: isActive ? Colors.white : Colors.white38,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              );
            }),
          ),
          
          const SizedBox(width: 16),
          
          // 다음 페이지 버튼
          IconButton(
            onPressed: _currentPage < _totalPages - 1 ? _goToNextPage : null,
            icon: Icon(
              Icons.chevron_right,
              color: _currentPage < _totalPages - 1 ? Colors.white : Colors.white24,
              size: 32,
            ),
          ),
        ],
      ),
    );
  }

  IconData _getAppIcon(String packageName) {
    if (packageName.contains('netflix')) return Icons.movie;
    if (packageName.contains('spotify') || packageName.contains('music')) return Icons.music_note;
    if (packageName.contains('map') || packageName.contains('navi') || packageName.contains('tmap')) return Icons.map;
    if (packageName.contains('youtube')) return Icons.play_circle;
    if (packageName.contains('chrome') || packageName.contains('browser')) return Icons.public;
    if (packageName.contains('phone') || packageName.contains('dialer')) return Icons.phone;
    if (packageName.contains('message') || packageName.contains('sms')) return Icons.message;
    if (packageName.contains('camera')) return Icons.camera_alt;
    if (packageName.contains('gallery') || packageName.contains('photo')) return Icons.photo;
    if (packageName.contains('setting')) return Icons.settings;
    return Icons.android;
  }
}

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
