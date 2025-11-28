import 'package:flutter/material.dart';
import 'native_service.dart';
import 'app_selection_screen.dart';
import 'package:get/get.dart';

class PipView extends StatefulWidget {
  final int displayId; // Logical ID for our app (1 or 2)
  final String label;

  const PipView({
    super.key,
    required this.displayId,
    required this.label,
  });

  @override
  State<PipView> createState() => _PipViewState();
}

class _PipViewState extends State<PipView> {
  int? _textureId;
  int? _virtualDisplayId;
  String? _currentPackage;
  String? _currentAppName;
  bool _isInitializing = false;
  String? _errorMessage;
  bool _initialized = false;

  @override
  void initState() {
    super.initState();
    // Delay initialization to ensure layout is complete
    Future.delayed(const Duration(milliseconds: 500), () {
      if (mounted) {
        _initializeVirtualDisplay();
      }
    });
  }

  Future<void> _initializeVirtualDisplay() async {
    if (_isInitializing || _initialized) return;
    
    setState(() {
      _isInitializing = true;
      _errorMessage = null;
    });

    try {
      // 화면 크기 기반 계산 (원본 앱 방식)
      final mediaQuery = MediaQuery.of(context);
      final screenSize = mediaQuery.size;
      
      // 가로모드 확인 - 아직 세로면 대기
      if (mediaQuery.orientation != Orientation.landscape) {
        print("Still in portrait mode, waiting for landscape...");
        setState(() {
          _isInitializing = false;
        });
        Future.delayed(const Duration(milliseconds: 300), () {
          if (mounted && !_initialized) {
            _initializeVirtualDisplay();
          }
        });
        return;
      }
      
      // 화면 전체 크기에서 PIP 영역 계산
      // 레이아웃: [Nav 80px][PIP1 50%][PIP2 50%]
      final totalWidth = screenSize.width;
      final totalHeight = screenSize.height;
      
      // 각 PIP 영역 = (전체 너비 - 네비 80px - 구분선 1px - 마진 16px*2) / 2
      var width = ((totalWidth - 80 - 1 - 32) / 2).toInt();
      var height = (totalHeight - 16).toInt(); // 상하 마진 8px*2
      
      // 안전장치: 여전히 세로 비율이면 swap
      if (height > width * 2) {
        print("Detected portrait ratio, swapping dimensions");
        final temp = width;
        width = height;
        height = temp;
      }

      if (width <= 0 || height <= 0) {
        print("Invalid dimensions detected: ${width}x$height. Retrying in 500ms...");
        setState(() {
          _isInitializing = false;
        });
        Future.delayed(const Duration(milliseconds: 500), () {
          if (mounted && !_initialized) {
            _initializeVirtualDisplay();
          }
        });
        return;
      }

      print("Creating VirtualDisplay for ${widget.label}: ${width}x$height (screen: ${totalWidth}x${totalHeight})");
      
      final result = await NativeService.createVirtualDisplay(width, height, 160);
      
      if (result == null) {
        throw Exception("VirtualDisplay creation returned null");
      }

      setState(() {
        _textureId = result['textureId'];
        _virtualDisplayId = result['displayId'];
        _initialized = true;
        _isInitializing = false;
      });
      
      print("VirtualDisplay created: texture=$_textureId, display=$_virtualDisplayId");
    } catch (e) {
      print("Error initializing VirtualDisplay: $e");
      setState(() {
        _errorMessage = e.toString();
        _isInitializing = false;
      });
    }
  }

  Future<void> _selectApp() async {
    if (_virtualDisplayId == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text("VirtualDisplay not ready")),
      );
      return;
    }

    try {
      final result = await Get.to(() => const AppSelectionScreen());
      if (result != null && result is Map) {
        final packageName = result['packageName'] as String?;
        final appName = result['appName'] as String?;
        
        if (packageName != null) {
          print("Launching $packageName on display $_virtualDisplayId");
          
          setState(() {
            _currentPackage = packageName;
            _currentAppName = appName ?? packageName;
          });
          
          final success = await NativeService.launchApp(packageName, _virtualDisplayId!);
          
          if (!success) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text("Failed to launch $appName")),
            );
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text("Launched $appName")),
            );
          }
        }
      }
    } catch (e) {
      print("Error selecting/launching app: $e");
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text("Error: $e")),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(8.0),
      decoration: BoxDecoration(
        color: Colors.black45,
        borderRadius: BorderRadius.circular(16.0),
        border: Border.all(color: Colors.white10),
      ),
      clipBehavior: Clip.hardEdge,
      child: Stack(
        children: [
          // Texture Layer or Error/Loading
          Positioned.fill(
            child: _buildContent(),
          ),

          // Overlay Controls
          Positioned(
            bottom: 16,
            right: 16,
            child: FloatingActionButton.small(
              onPressed: _selectApp,
              tooltip: "Select App",
              child: const Icon(Icons.apps),
            ),
          ),
          
          // Info Label
          Positioned(
            top: 16,
            left: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black87,
                borderRadius: BorderRadius.circular(4),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    widget.label,
                    style: const TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.bold),
                  ),
                  if (_currentAppName != null)
                    Text(
                      _currentAppName!,
                      style: const TextStyle(color: Colors.greenAccent, fontSize: 10),
                    ),
                  if (_virtualDisplayId != null)
                    Text(
                      "Display ID: $_virtualDisplayId",
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildContent() {
    if (_errorMessage != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.redAccent),
              const SizedBox(height: 16),
              Text(
                "Error",
                style: Theme.of(context).textTheme.titleLarge?.copyWith(color: Colors.redAccent),
              ),
              const SizedBox(height: 8),
              Text(
                _errorMessage!,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.white70, fontSize: 12),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () {
                  setState(() {
                    _errorMessage = null;
                    _initialized = false;
                  });
                  _initializeVirtualDisplay();
                },
                child: const Text("Retry"),
              ),
            ],
          ),
        ),
      );
    }

    if (_isInitializing || _textureId == null) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text("Initializing VirtualDisplay...", style: TextStyle(color: Colors.white70)),
          ],
        ),
      );
    }

    // Note: Touch input injection requires system app privileges (INJECT_EVENTS permission)
    // VirtualDisplay apps receive touch input directly from Android system
    // Our app displays the VirtualDisplay content, but touch goes to the app on that display
    return Texture(textureId: _textureId!);
  }
}
