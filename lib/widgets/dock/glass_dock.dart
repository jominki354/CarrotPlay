import 'dart:ui';
import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';
import 'digital_clock.dart';
import 'dock_item.dart';
import '../../connectivity_service.dart';

/// The main navigation dock on the left side.
/// Project Fresh Carrot
class GlassDock extends StatelessWidget {
  final int selectedIndex;
  final Function(int) onItemSelected;
  final VoidCallback onAppDrawerTap;
  final bool isAppDrawerOpen;
  final bool isFullscreenApp;
  final VoidCallback onCloseApp;

  const GlassDock({
    super.key,
    required this.selectedIndex,
    required this.onItemSelected,
    required this.onAppDrawerTap,
    required this.isAppDrawerOpen,
    required this.isFullscreenApp,
    required this.onCloseApp,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: AppDimens.dockWidth,
      height: double.infinity,
      decoration: BoxDecoration(
        color: AppColors.glassGrey.withOpacity(0.8),
        border: const Border(
          right: BorderSide(
            color: Colors.white10,
            width: 1,
          ),
        ),
      ),
      child: ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
          child: SafeArea(
            child: Column(
              children: [
                const SizedBox(height: AppDimens.paddingMedium),
                
                // Clock
                const DigitalClock(),
                
                const SizedBox(height: AppDimens.paddingSmall),
                
                // Network Status (Simplified)
                const _NetworkStatusIndicator(),
                
                const Spacer(),
                
                // Presets
                DockItem(
                  icon: Icons.grid_view_rounded, // Preset 1
                  isSelected: selectedIndex == 0 && !isFullscreenApp && !isAppDrawerOpen,
                  onTap: () => onItemSelected(0),
                ),
                const SizedBox(height: AppDimens.paddingSmall),
                DockItem(
                  icon: Icons.splitscreen_rounded, // Preset 2
                  isSelected: selectedIndex == 1 && !isFullscreenApp && !isAppDrawerOpen,
                  onTap: () => onItemSelected(1),
                  activeColor: AppColors.successGreen,
                ),
                const SizedBox(height: AppDimens.paddingSmall),
                DockItem(
                  icon: Icons.dashboard_customize_rounded, // Preset 3
                  isSelected: selectedIndex == 2 && !isFullscreenApp && !isAppDrawerOpen,
                  onTap: () => onItemSelected(2),
                  activeColor: AppColors.infoBlue,
                ),
                
                const Spacer(),
                
                // App Drawer / Close Button
                DockItem(
                  icon: isFullscreenApp || isAppDrawerOpen 
                      ? Icons.close_rounded 
                      : Icons.apps_rounded,
                  isSelected: isAppDrawerOpen || isFullscreenApp,
                  onTap: isFullscreenApp ? onCloseApp : onAppDrawerTap,
                  activeColor: AppColors.textPrimary,
                ),
                
                const SizedBox(height: AppDimens.paddingLarge),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _NetworkStatusIndicator extends StatelessWidget {
  const _NetworkStatusIndicator();

  @override
  Widget build(BuildContext context) {
    final connectivity = ConnectivityService();
    
    return StreamBuilder<NetworkStatus>(
      stream: connectivity.statusStream,
      initialData: connectivity.currentStatus,
      builder: (context, snapshot) {
        final status = snapshot.data!;
        return Column(
          children: [
            Icon(
              status.isWifi ? Icons.wifi_rounded : Icons.signal_cellular_alt_rounded,
              size: 16,
              color: status.isConnected ? Colors.white70 : Colors.white30,
            ),
            const SizedBox(height: 2),
            Text(
              status.isWifi ? 'WiFi' : 'LTE',
              style: const TextStyle(
                fontSize: 10,
                color: Colors.white54,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        );
      },
    );
  }
}
