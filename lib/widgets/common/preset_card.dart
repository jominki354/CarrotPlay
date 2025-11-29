import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';
import '../../theme/app_text_styles.dart';
import '../animations/animated_glass_card.dart';
import '../animations/bouncy_button.dart';

/// A card representing a preset configuration.
/// Project Fresh Carrot
class PresetCard extends StatelessWidget {
  final String title;
  final String description;
  final bool isSelected;
  final VoidCallback onTap;
  final Widget? icon;

  const PresetCard({
    super.key,
    required this.title,
    required this.description,
    required this.isSelected,
    required this.onTap,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return BouncyButton(
      onPressed: onTap,
      child: AnimatedGlassCard(
        isSelected: isSelected,
        padding: const EdgeInsets.all(AppDimens.paddingMedium),
        child: Row(
          children: [
            if (icon != null) ...[
              icon!,
              const SizedBox(width: AppDimens.paddingMedium),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: AppTextStyles.header3.copyWith(
                      color: isSelected ? AppColors.carrotOrange : Colors.white,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                    style: AppTextStyles.caption.copyWith(
                      color: isSelected ? Colors.white70 : AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            if (isSelected)
              const Icon(
                Icons.check_circle_rounded,
                color: AppColors.carrotOrange,
                size: 24,
              ),
          ],
        ),
      ),
    );
  }
}
