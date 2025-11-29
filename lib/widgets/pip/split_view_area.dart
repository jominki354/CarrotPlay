import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../preset_service.dart';
import '../../pip_view.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';

/// The main split view area containing two PIP views and a resizer.
/// Project Fresh Carrot
class SplitViewArea extends StatefulWidget {
  final PresetService presetService;
  final GlobalKey<PipViewState> pip1Key;
  final GlobalKey<PipViewState> pip2Key;

  const SplitViewArea({
    super.key,
    required this.presetService,
    required this.pip1Key,
    required this.pip2Key,
  });

  @override
  State<SplitViewArea> createState() => _SplitViewAreaState();
}

class _SplitViewAreaState extends State<SplitViewArea> {
  @override
  Widget build(BuildContext context) {
    final preset = widget.presetService.currentPreset;
    final leftFlex = (preset.leftRatio * 100).round();
    final rightFlex = (preset.rightRatio * 100).round();

    return Container(
      color: Colors.transparent, // Background handled by main layout
      padding: const EdgeInsets.all(AppDimens.paddingSmall),
      child: LayoutBuilder(
        builder: (context, constraints) {
          return Stack(
            children: [
              Row(
                children: [
                  Expanded(
                    flex: leftFlex,
                    child: PipView(
                      key: widget.pip1Key,
                      displayId: 1,
                      label: "PIP 1",
                    ),
                  ),
                  const SizedBox(width: AppDimens.paddingSmall),
                  Expanded(
                    flex: rightFlex,
                    child: PipView(
                      key: widget.pip2Key,
                      displayId: 2,
                      label: "PIP 2",
                    ),
                  ),
                ],
              ),
              // Resizer Overlay
              Positioned(
                left: constraints.maxWidth * preset.leftRatio - 12, // Center the touch target
                top: 0,
                bottom: 0,
                width: 24, // Wide touch target
                child: _RatioResizer(
                  currentRatio: preset.leftRatio,
                  maxWidth: constraints.maxWidth,
                  onRatioChanged: (newRatio) {
                    final snapped = (newRatio * 20).round() / 20.0;
                    final clampedRatio = snapped.clamp(0.3, 0.7);
                    widget.presetService.updateLeftRatio(
                      widget.presetService.selectedIndex, 
                      clampedRatio
                    );
                    setState(() {}); // Rebuild to update flex
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _RatioResizer extends StatefulWidget {
  final double currentRatio;
  final double maxWidth;
  final ValueChanged<double> onRatioChanged;

  const _RatioResizer({
    required this.currentRatio,
    required this.maxWidth,
    required this.onRatioChanged,
  });

  @override
  State<_RatioResizer> createState() => _RatioResizerState();
}

class _RatioResizerState extends State<_RatioResizer> {
  bool _isDragging = false;
  bool _isHovering = false;
  bool _canDrag = false;
  Timer? _longPressTimer;

  void _startLongPressTimer() {
    _longPressTimer?.cancel();
    _longPressTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted) {
        setState(() {
          _canDrag = true;
        });
        HapticFeedback.mediumImpact();
      }
    });
  }

  void _cancelLongPressTimer() {
    _longPressTimer?.cancel();
    if (!_isDragging) {
      setState(() {
        _canDrag = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      onPanStart: (details) {
        _startLongPressTimer();
      },
      onPanUpdate: (details) {
        if (_canDrag) {
          setState(() {
            _isDragging = true;
          });
          final newRatio = (details.localPosition.dx + (widget.currentRatio * widget.maxWidth)) / widget.maxWidth;
          // Since we are in a Positioned widget relative to the left, we need to calculate absolute position
          // But actually, GestureDetector in Positioned gets local delta.
          // Let's use a simpler approach: calculate ratio based on global position relative to the Stack
          
          // Better approach: The parent passes the ratio update.
          // We need the delta to update the ratio.
          final deltaRatio = details.delta.dx / widget.maxWidth;
          widget.onRatioChanged(widget.currentRatio + deltaRatio);
        }
      },
      onPanEnd: (details) {
        _cancelLongPressTimer();
        setState(() {
          _isDragging = false;
          _canDrag = false;
        });
      },
      onPanCancel: () {
        _cancelLongPressTimer();
        setState(() {
          _isDragging = false;
          _canDrag = false;
        });
      },
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovering = true),
        onExit: (_) => setState(() => _isHovering = false),
        child: Center(
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            width: _canDrag || _isDragging ? 6.0 : 4.0,
            height: _canDrag || _isDragging ? 64.0 : 48.0,
            decoration: BoxDecoration(
              color: _canDrag || _isDragging 
                  ? AppColors.carrotOrange 
                  : (_isHovering ? Colors.white54 : Colors.white24),
              borderRadius: BorderRadius.circular(3.0),
              boxShadow: _canDrag || _isDragging
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
    );
  }
}
