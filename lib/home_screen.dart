import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'pip_view.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _selectedIndex = 0;

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
    
    return Scaffold(
      body: SafeArea(
        child: Row(
          children: [
            // Left Navigation Bar (고정 폭)
            Container(
              width: 80, // 고정 폭으로 변경
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.surface,
                border: Border(
                  right: BorderSide(color: Colors.white10, width: 1),
                ),
              ),
              child: NavigationRail(
                selectedIndex: _selectedIndex,
                onDestinationSelected: (int index) {
                  setState(() {
                    _selectedIndex = index;
                  });
                },
                labelType: NavigationRailLabelType.all,
                backgroundColor: Colors.transparent,
                destinations: const [
                  NavigationRailDestination(
                    icon: Icon(Icons.home),
                    label: Text('Home'),
                  ),
                  NavigationRailDestination(
                    icon: Icon(Icons.apps),
                    label: Text('Apps'),
                  ),
                  NavigationRailDestination(
                    icon: Icon(Icons.settings),
                    label: Text('Settings'),
                  ),
                ],
              ),
            ),
            
            // Right Split View (나머지 공간 사용)
            Expanded(
              child: Row(
                children: [
                  // PIP Area 1
                  Expanded(
                    child: PipView(
                      displayId: 1,
                      label: "Primary App",
                    ),
                  ),
                  Container(
                    width: 1,
                    color: Colors.white10,
                  ),
                  // PIP Area 2
                  Expanded(
                    child: PipView(
                      displayId: 2,
                      label: "Secondary App",
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
