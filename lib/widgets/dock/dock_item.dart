import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';
import '../animations/bouncy_button.dart';

/// An item in the GlassDock.
/// Project Fresh Carrot
class DockItem extends StatelessWidget {
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;
  final Color? activeColor;

  const DockItem({
    super.key,
    required this.icon,
    required this.isSelected,
    required this.onTap,
    this.onLongPress,
    this.activeColor,
  });

  @override
  Widget build(BuildContext context) {
    final color = activeColor ?? AppColors.carrotOrange;

    return BouncyButton(
      onPressed: onTap,
      onLongPress: onLongPress,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        width: 56,
        height: 56,
        decoration: BoxDecoration(
          color: isSelected 
              ? color.withOpacity(0.2) 
              : Colors.transparent,
          borderRadius: BorderRadius.circular(AppDimens.radiusMedium),
          border: Border.all(
            color: isSelected 
                ? color 
                : Colors.transparent,
            width: 1.5,
          ),
        ),
        child: Icon(
          icon,
          color: isSelected ? Colors.white : AppColors.textSecondary,
          size: 28,
        ),
      ),
    );
  }
}
