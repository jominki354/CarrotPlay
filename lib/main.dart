import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Force landscape orientation (차량용 디스플레이) - await로 완료 보장
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  
  // 전체화면 모드 (상태바/네비게이션바 숨김)
  await SystemChrome.setEnabledSystemUIMode(
    SystemUiMode.immersiveSticky,
    overlays: [],
  );
  
  // Set system UI overlay style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Colors.black,
    ),
  );
  
  runApp(const CarCarLauncherApp());
}

// 앱 라이프사이클 관찰자 - 가로모드 유지
class OrientationObserver extends WidgetsBindingObserver {
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _enforceOrientation();
    }
  }

  @override
  void didChangeMetrics() {
    _enforceOrientation();
  }

  void _enforceOrientation() {
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    SystemChrome.setEnabledSystemUIMode(
      SystemUiMode.immersiveSticky,
      overlays: [],
    );
  }
}

class CarCarLauncherApp extends StatefulWidget {
  const CarCarLauncherApp({super.key});

  @override
  State<CarCarLauncherApp> createState() => _CarCarLauncherAppState();
}

class _CarCarLauncherAppState extends State<CarCarLauncherApp> {
  late final OrientationObserver _orientationObserver;

  @override
  void initState() {
    super.initState();
    _orientationObserver = OrientationObserver();
    WidgetsBinding.instance.addObserver(_orientationObserver);
    // 초기화 시 한번 더 강제
    _orientationObserver._enforceOrientation();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(_orientationObserver);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GetMaterialApp(
      title: 'CarCar Launcher',
      debugShowCheckedModeBanner: false, // Remove debug banner
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.dark(
          primary: Colors.blueAccent,
          secondary: Colors.cyanAccent,
          surface: const Color(0xFF1E1E1E),
          background: const Color(0xFF121212),
        ),
        scaffoldBackgroundColor: const Color(0xFF121212),
      ),
      home: const HomeScreen(),
    );
  }
}
