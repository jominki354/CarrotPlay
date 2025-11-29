import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'preset_service.dart';
import 'app_drawer_content.dart';
import 'theme/app_colors.dart';
import 'theme/app_dimens.dart';
import 'widgets/common/app_icon_wrapper.dart';

class PresetEditor extends StatefulWidget {
  final int presetIndex;
  final PresetService presetService;

  const PresetEditor({
    super.key,
    required this.presetIndex,
    required this.presetService,
  });

  @override
  State<PresetEditor> createState() => _PresetEditorState();
}

class _PresetEditorState extends State<PresetEditor> {
  late PipAppConfig _pip1Config;
  late PipAppConfig _pip2Config;
  late double _leftRatio;

  @override
  void initState() {
    super.initState();
    debugPrint('PresetEditor initState called');
    final preset = widget.presetService.presets[widget.presetIndex];
    _pip1Config = preset.pip1;
    _pip2Config = preset.pip2;
    _leftRatio = preset.leftRatio;
  }

  void _selectAppForPip(int pipIndex) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => _AppSelectorSheet(
        onSelect: (app) {
          setState(() {
            final config = PipAppConfig(
              packageName: app.packageName,
              appName: app.appName,
              icon: app.icon,
              scale: pipIndex == 1 ? _pip1Config.scale : _pip2Config.scale,
            );
            if (pipIndex == 1) {
              _pip1Config = config;
            } else {
              _pip2Config = config;
            }
          });
          Navigator.pop(context);
        },
        onClear: () {
          setState(() {
            if (pipIndex == 1) {
              _pip1Config = PipAppConfig();
            } else {
              _pip2Config = PipAppConfig();
            }
          });
          Navigator.pop(context);
        },
      ),
    );
  }

  void _resetToDefault() {
    HapticFeedback.mediumImpact();
    setState(() {
      _pip1Config = PipAppConfig();
      _pip2Config = PipAppConfig();
      _leftRatio = 0.5;
    });
  }

  void _save() {
    final newPreset = PresetConfig(
      name: '${widget.presetIndex + 1}',
      pip1: _pip1Config,
      pip2: _pip2Config,
      leftRatio: _leftRatio,
    );
    widget.presetService.updatePreset(widget.presetIndex, newPreset);
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    debugPrint('=== PresetEditor build() ===');
    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;
    final isLandscape = screenWidth > screenHeight;
    // 가로모드에서는 더 작은 높이 사용
    final sheetHeight = isLandscape ? screenHeight * 0.85 : screenHeight * 0.6;
    
    final leftPercent = (_leftRatio * 100).round();
    final rightPercent = ((1 - _leftRatio) * 100).round();

    return Container(
      height: sheetHeight,
      constraints: BoxConstraints(maxHeight: screenHeight * 0.9),
      decoration: const BoxDecoration(
        color: Color(0xFF1C1C1E),
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 드래그 핸들
          Container(
            margin: const EdgeInsets.only(top: 8),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: Colors.white24,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: 12),
          
          // 헤더
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Text(
                  '프리셋 ${widget.presetIndex + 1}',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: Colors.white,
                  ),
                ),
                const Spacer(),
                GestureDetector(
                  onTap: _resetToDefault,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                    decoration: BoxDecoration(
                      color: Colors.white10,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Text('초기화', style: TextStyle(color: Colors.white70, fontSize: 13)),
                  ),
                ),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: _save,
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                    decoration: BoxDecoration(
                      color: AppColors.carrotOrange,
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Text('저장', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          
          // 내용
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // 비율 프리뷰
                  Container(
                    height: 80,
                    decoration: BoxDecoration(
                      color: Colors.black26,
                      borderRadius: BorderRadius.circular(10),
                      border: Border.all(color: Colors.white10),
                    ),
                    child: Row(
                      children: [
                        Expanded(
                          flex: 1,
                          child: Container(
                            margin: const EdgeInsets.all(4),
                            decoration: BoxDecoration(
                              color: _pip1Config.isNotEmpty 
                                  ? AppColors.carrotOrange.withOpacity(0.3)
                                  : Colors.white10,
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(color: AppColors.carrotOrange.withOpacity(0.5), width: 2),
                            ),
                            child: Center(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.looks_one, color: AppColors.carrotOrange, size: 22),
                                  Text('$leftPercent%', style: const TextStyle(color: AppColors.carrotOrange, fontSize: 12)),
                                ],
                              ),
                            ),
                          ),
                        ),
                        Container(width: 2, color: Colors.white24, margin: const EdgeInsets.symmetric(vertical: 8)),
                        Expanded(
                          flex: 1,
                          child: Container(
                            margin: const EdgeInsets.all(4),
                            decoration: BoxDecoration(
                              color: _pip2Config.isNotEmpty 
                                  ? AppColors.successGreen.withOpacity(0.3)
                                  : Colors.white10,
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(color: AppColors.successGreen.withOpacity(0.5), width: 2),
                            ),
                            child: Center(
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.looks_two, color: AppColors.successGreen, size: 22),
                                  Text('$rightPercent%', style: const TextStyle(color: AppColors.successGreen, fontSize: 12)),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  
                  // 비율 슬라이더
                  Row(
                    children: [
                      Text('$leftPercent%', style: const TextStyle(color: AppColors.carrotOrange, fontWeight: FontWeight.w600, fontSize: 13)),
                      Expanded(
                        child: SliderTheme(
                          data: SliderTheme.of(context).copyWith(
                            trackHeight: 4,
                            thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
                          ),
                          child: Slider(
                            value: _leftRatio,
                            min: 0.3,
                            max: 0.7,
                            divisions: 8,
                            activeColor: AppColors.carrotOrange,
                            inactiveColor: AppColors.successGreen.withOpacity(0.3),
                            onChanged: (value) {
                              HapticFeedback.selectionClick();
                              setState(() => _leftRatio = value);
                            },
                          ),
                        ),
                      ),
                      Text('$rightPercent%', style: const TextStyle(color: AppColors.successGreen, fontWeight: FontWeight.w600, fontSize: 13)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  
                  // PIP 1 앱 선택
                  _buildAppSelector(1, _pip1Config, AppColors.carrotOrange),
                  const SizedBox(height: 8),
                  
                  // PIP 2 앱 선택
                  _buildAppSelector(2, _pip2Config, AppColors.successGreen),
                  const SizedBox(height: 16),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppSelector(int pipIndex, PipAppConfig config, Color color) {
    return GestureDetector(
      onTap: () => _selectAppForPip(pipIndex),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white.withOpacity(0.05),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: config.isNotEmpty ? color.withOpacity(0.5) : Colors.white10),
        ),
        child: Row(
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 8),
            Text('화면 $pipIndex', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13)),
            const Spacer(),
            if (config.isNotEmpty) ...[
              if (config.icon != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(6),
                  child: Image.memory(config.icon!, width: 28, height: 28, fit: BoxFit.cover),
                ),
              const SizedBox(width: 6),
              Flexible(
                child: Text(
                  config.appName ?? '',
                  style: const TextStyle(color: Colors.white70, fontSize: 12),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ] else
              const Text('앱 선택', style: TextStyle(color: Colors.white38, fontSize: 12)),
            const SizedBox(width: 4),
            const Icon(Icons.chevron_right, color: Colors.white38, size: 18),
          ],
        ),
      ),
    );
  }
}

class _AppSelectorSheet extends StatelessWidget {
  final void Function(AppInfo) onSelect;
  final VoidCallback onClear;

  const _AppSelectorSheet({
    required this.onSelect,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    final apps = AppCache().apps;
    final screenHeight = MediaQuery.of(context).size.height;
    final isLandscape = MediaQuery.of(context).orientation == Orientation.landscape;
    
    return Container(
      height: isLandscape ? screenHeight * 0.85 : screenHeight * 0.8,
      decoration: const BoxDecoration(
        color: Color(0xFF2C2C2E),
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Header
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('앱 선택', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white)),
                TextButton(
                  onPressed: onClear,
                  child: const Text('비우기', style: TextStyle(color: AppColors.errorRed, fontSize: 13)),
                ),
              ],
            ),
          ),
          
          // Grid
          Expanded(
            child: apps.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : GridView.builder(
                    padding: const EdgeInsets.all(16),
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: isLandscape ? 8 : 4,
                      mainAxisSpacing: 16,
                      crossAxisSpacing: 16,
                      childAspectRatio: 0.8,
                    ),
                    itemCount: apps.length,
                    itemBuilder: (context, index) {
                      final app = apps[index];
                      return GestureDetector(
                        onTap: () => onSelect(app),
                        child: Column(
                          children: [
                            AppIconWrapper(
                              iconData: app.icon,
                              size: 56,
                              radius: AppDimens.radiusMedium,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              app.appName,
                              style: const TextStyle(fontSize: 12, color: Colors.white70),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              textAlign: TextAlign.center,
                            ),
                          ],
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
