import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A button wrapper that provides a bouncy scale animation on press.
/// Project Fresh Carrot
/// 
/// 터치 인식 개선:
/// - TapDown 즉시 반응
/// - 짧은 터치도 인식 (최소 터치 시간 제거)
/// - HitTestBehavior.opaque로 투명 영역도 터치 가능
class BouncyButton extends StatefulWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final double scaleFactor;
  final Duration duration;

  const BouncyButton({
    super.key,
    required this.child,
    this.onPressed,
    this.onLongPress,
    this.scaleFactor = 0.95,
    this.duration = const Duration(milliseconds: 80), // 더 빠른 반응
  });

  @override
  State<BouncyButton> createState() => _BouncyButtonState();
}

class _BouncyButtonState extends State<BouncyButton> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  bool _isPressed = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: widget.duration,
    );
    
    _scaleAnimation = Tween<double>(
      begin: 1.0,
      end: widget.scaleFactor,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeOutCubic,
    ));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onTapDown(TapDownDetails details) {
    if (widget.onPressed != null || widget.onLongPress != null) {
      _isPressed = true;
      _controller.forward();
      HapticFeedback.selectionClick(); // 더 가벼운 햅틱
    }
  }

  void _onTapUp(TapUpDetails details) {
    if (_isPressed) {
      _isPressed = false;
      _controller.reverse();
    }
  }

  void _onTapCancel() {
    if (_isPressed) {
      _isPressed = false;
      _controller.reverse();
    }
  }

  void _onTap() {
    // TapUp 후 즉시 실행
    widget.onPressed?.call();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: _onTapDown,
      onTapUp: _onTapUp,
      onTapCancel: _onTapCancel,
      onTap: _onTap,
      onLongPress: widget.onLongPress,
      behavior: HitTestBehavior.opaque,
      // 터치 영역 확장을 위한 최소 크기 보장
      child: ConstrainedBox(
        constraints: const BoxConstraints(
          minWidth: 44, // Apple HIG 최소 터치 영역
          minHeight: 44,
        ),
        child: ScaleTransition(
          scale: _scaleAnimation,
          child: widget.child,
        ),
      ),
    );
  }
}
