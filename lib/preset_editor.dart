import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'preset_service.dart';
import 'app_drawer_content.dart';

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
    // 가로모드에서 높이 적응
    final screenHeight = MediaQuery.of(context).size.height;
    final isLandscape = MediaQuery.of(context).orientation == Orientation.landscape;
    final sheetHeight = isLandscape ? screenHeight * 0.9 : screenHeight * 0.6;
    
    return Container(
      height: sheetHeight,
      decoration: const BoxDecoration(
        color: Color(0xFF1A1A1A),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        children: [
          // 헤더
          _buildHeader(),
          
          // 콘텐츠 (스크롤 가능)
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              child: isLandscape 
                  ? _buildLandscapeLayout() 
                  : _buildPortraitLayout(),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: Colors.white12)),
      ),
      child: Row(
        children: [
          // 드래그 핸들
          Expanded(
            child: Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.white30,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// 가로모드 레이아웃: 좌우로 PIP 설정 배치
  Widget _buildLandscapeLayout() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 타이틀 + 저장 버튼
        Row(
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
            ElevatedButton(
              onPressed: _save,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blueAccent,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
              ),
              child: const Text('저장'),
            ),
          ],
        ),
        const SizedBox(height: 16),
        
        // 좌우 비율 선택
        _buildRatioSelector(),
        const SizedBox(height: 16),
        
        // PIP 1 & PIP 2 가로 배치
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: _buildPipCard(1, _pip1Config, Colors.blueAccent)),
            const SizedBox(width: 16),
            Expanded(child: _buildPipCard(2, _pip2Config, Colors.greenAccent)),
          ],
        ),
      ],
    );
  }

  /// 세로모드 레이아웃
  Widget _buildPortraitLayout() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 타이틀 + 저장 버튼
        Row(
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
            ElevatedButton(
              onPressed: _save,
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blueAccent,
                foregroundColor: Colors.white,
              ),
              child: const Text('저장'),
            ),
          ],
        ),
        const SizedBox(height: 16),
        
        // 좌우 비율 선택
        _buildRatioSelector(),
        const SizedBox(height: 16),
        
        // PIP 1
        _buildPipCard(1, _pip1Config, Colors.blueAccent),
        const SizedBox(height: 12),
        
        // PIP 2
        _buildPipCard(2, _pip2Config, Colors.greenAccent),
      ],
    );
  }

  Widget _buildRatioSelector() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.view_column, color: Colors.white54, size: 18),
              const SizedBox(width: 8),
              const Text(
                '좌우 비율',
                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500, color: Colors.white70),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.blueAccent.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${(_leftRatio * 100).round()}:${((1 - _leftRatio) * 100).round()}',
                  style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.blueAccent),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          
          // 비율 버튼들 (가로 스크롤)
          SizedBox(
            height: 36,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: SplitRatio.options.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final option = SplitRatio.options[index];
                final isSelected = (_leftRatio - option.leftRatio).abs() < 0.01;
                return InkWell(
                  onTap: () => setState(() => _leftRatio = option.leftRatio),
                  borderRadius: BorderRadius.circular(6),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    decoration: BoxDecoration(
                      color: isSelected ? Colors.blueAccent : Colors.white.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Center(
                      child: Text(
                        option.label,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                          color: isSelected ? Colors.white : Colors.white60,
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
          const SizedBox(height: 12),
          
          // 비율 미리보기 바
          Container(
            height: 20,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: Colors.white24),
            ),
            child: Row(
              children: [
                Expanded(
                  flex: (_leftRatio * 100).round(),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.blueAccent.withOpacity(0.6),
                      borderRadius: const BorderRadius.horizontal(left: Radius.circular(3)),
                    ),
                    child: const Center(
                      child: Text('L', style: TextStyle(fontSize: 10, color: Colors.white70)),
                    ),
                  ),
                ),
                Expanded(
                  flex: ((1 - _leftRatio) * 100).round(),
                  child: Container(
                    decoration: BoxDecoration(
                      color: Colors.greenAccent.withOpacity(0.6),
                      borderRadius: const BorderRadius.horizontal(right: Radius.circular(3)),
                    ),
                    child: const Center(
                      child: Text('R', style: TextStyle(fontSize: 10, color: Colors.white70)),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPipCard(int pipIndex, PipAppConfig config, Color accentColor) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.05),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: accentColor.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          // 헤더
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: accentColor,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                'PIP $pipIndex (${pipIndex == 1 ? "좌측" : "우측"})',
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Colors.white),
              ),
            ],
          ),
          const SizedBox(height: 12),
          
          // 앱 선택 버튼
          InkWell(
            onTap: () => _selectAppForPip(pipIndex),
            borderRadius: BorderRadius.circular(8),
            child: Container(
              height: 56,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.05),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: config.isNotEmpty ? accentColor.withOpacity(0.5) : Colors.white24),
              ),
              child: config.isNotEmpty
                  ? Row(
                      children: [
                        // 앱 아이콘
                        Container(
                          width: 40,
                          height: 40,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(8),
                            color: Colors.white.withOpacity(0.1),
                          ),
                          child: config.icon != null
                              ? ClipRRect(
                                  borderRadius: BorderRadius.circular(8),
                                  child: Image.memory(config.icon!, fit: BoxFit.cover),
                                )
                              : const Icon(Icons.android, color: Colors.white54, size: 24),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Text(
                            config.appName ?? '',
                            style: const TextStyle(fontSize: 13, color: Colors.white),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const Icon(Icons.chevron_right, color: Colors.white38, size: 20),
                      ],
                    )
                  : const Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.add_circle_outline, color: Colors.white38, size: 20),
                        SizedBox(width: 8),
                        Text('앱 선택', style: TextStyle(color: Colors.white38)),
                      ],
                    ),
            ),
          ),
          
          // 크기 조절 (앱이 선택된 경우에만)
          if (config.isNotEmpty) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                const Text('크기', style: TextStyle(fontSize: 12, color: Colors.white54)),
                const Spacer(),
                Text(
                  '${(config.scale * 100).round()}%',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: accentColor),
                ),
              ],
            ),
            const SizedBox(height: 4),
            SliderTheme(
              data: SliderTheme.of(context).copyWith(
                trackHeight: 4,
                thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 8),
                overlayShape: const RoundSliderOverlayShape(overlayRadius: 16),
              ),
              child: Slider(
                value: config.scale,
                min: 0.5,
                max: 1.5,
                divisions: 20,
                activeColor: accentColor,
                inactiveColor: Colors.white24,
                onChanged: (value) {
                  final snapped = (value * 20).round() / 20.0;
                  setState(() {
                    if (pipIndex == 1) {
                      _pip1Config = _pip1Config.copyWith(scale: snapped);
                    } else {
                      _pip2Config = _pip2Config.copyWith(scale: snapped);
                    }
                  });
                },
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// 앱 선택 바텀시트
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
      height: isLandscape ? screenHeight * 0.85 : screenHeight * 0.7,
      decoration: const BoxDecoration(
        color: Color(0xFF252525),
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        children: [
          // 헤더
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: const BoxDecoration(
              border: Border(bottom: BorderSide(color: Colors.white12)),
            ),
            child: Row(
              children: [
                const Text(
                  '앱 선택',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: Colors.white),
                ),
                const Spacer(),
                TextButton(
                  onPressed: onClear,
                  child: const Text('비우기', style: TextStyle(color: Colors.redAccent)),
                ),
              ],
            ),
          ),
          
          // 앱 그리드
          Expanded(
            child: apps.isEmpty
                ? const Center(child: CircularProgressIndicator(color: Colors.white38))
                : GridView.builder(
                    padding: const EdgeInsets.all(12),
                    gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                      crossAxisCount: isLandscape ? 8 : 5,
                      mainAxisSpacing: 12,
                      crossAxisSpacing: 12,
                      childAspectRatio: 0.85,
                    ),
                    itemCount: apps.length,
                    itemBuilder: (context, index) {
                      final app = apps[index];
                      return _buildAppItem(app);
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppItem(AppInfo app) {
    return InkWell(
      onTap: () => onSelect(app),
      borderRadius: BorderRadius.circular(8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          // 아이콘 (80% 크기)
          Expanded(
            flex: 3,
            child: Center(
              child: FractionallySizedBox(
                widthFactor: 0.8,
                heightFactor: 0.8,
                child: app.icon != null
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.memory(app.icon!, fit: BoxFit.cover),
                      )
                    : Container(
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(Icons.android, color: Colors.white54, size: 28),
                      ),
              ),
            ),
          ),
          // 앱 이름
          Expanded(
            flex: 1,
            child: Center(
              child: Text(
                app.appName,
                style: const TextStyle(fontSize: 10, color: Colors.white70),
                maxLines: 1,
                textAlign: TextAlign.center,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
