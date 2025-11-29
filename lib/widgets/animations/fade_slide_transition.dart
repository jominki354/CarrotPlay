import 'package:flutter/material.dart';

/// A transition that combines fade and slide effects.
/// Useful for page transitions and modal dialogs.
/// Project Fresh Carrot
class FadeSlideTransition extends StatelessWidget {
  final Animation<double> animation;
  final Widget child;
  final Offset beginOffset;

  const FadeSlideTransition({
    super.key,
    required this.animation,
    required this.child,
    this.beginOffset = const Offset(0.0, 0.1), // Slight slide up from bottom
  });

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: animation,
      child: SlideTransition(
        position: Tween<Offset>(
          begin: beginOffset,
          end: Offset.zero,
        ).animate(CurvedAnimation(
          parent: animation,
          curve: Curves.easeOutQuart,
        )),
        child: child,
      ),
    );
  }
}
