import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';

/// A custom slider with CarrotPlay styling.
/// Project Fresh Carrot
class CarrotSlider extends StatelessWidget {
  final double value;
  final ValueChanged<double> onChanged;
  final double min;
  final double max;
  final int? divisions;
  final String? label;

  const CarrotSlider({
    super.key,
    required this.value,
    required this.onChanged,
    this.min = 0.0,
    this.max = 1.0,
    this.divisions,
    this.label,
  });

  @override
  Widget build(BuildContext context) {
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        activeTrackColor: AppColors.carrotOrange,
        inactiveTrackColor: Colors.white.withOpacity(0.2),
        thumbColor: Colors.white,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 10.0),
        overlayColor: AppColors.carrotOrange.withOpacity(0.2),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 20.0),
        trackHeight: 6.0,
        valueIndicatorColor: AppColors.carrotOrange,
        valueIndicatorTextStyle: const TextStyle(
          color: Colors.white,
          fontWeight: FontWeight.bold,
        ),
      ),
      child: Slider(
        value: value,
        onChanged: onChanged,
        min: min,
        max: max,
        divisions: divisions,
        label: label,
      ),
    );
  }
}
