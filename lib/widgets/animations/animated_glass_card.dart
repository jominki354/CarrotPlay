import 'dart:ui';
import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';

/// A card with glassmorphism effect that animates its appearance on state change.
/// Project Fresh Carrot
class AnimatedGlassCard extends StatelessWidget {
  final Widget child;
  final bool isSelected;
  final VoidCallback? onTap;
  final double width;
  final double? height;
  final EdgeInsetsGeometry padding;
  final double borderRadius;

  const AnimatedGlassCard({
    super.key,
    required this.child,
    this.isSelected = false,
    this.onTap,
    this.width = double.infinity,
    this.height,
    this.padding = const EdgeInsets.all(AppDimens.paddingMedium),
    this.borderRadius = AppDimens.radiusLarge,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
            width: width,
            height: height,
            padding: padding,
            decoration: BoxDecoration(
              color: isSelected 
                  ? AppColors.carrotOrange.withOpacity(0.15) 
                  : AppColors.glassGrey.withOpacity(0.6),
              borderRadius: BorderRadius.circular(borderRadius),
              border: Border.all(
                color: isSelected 
                    ? AppColors.carrotOrange 
                    : Colors.white.withOpacity(0.1),
                width: isSelected ? 1.5 : 1.0,
              ),
            ),
            child: child,
          ),
        ),
      ),
    );
  }
}
