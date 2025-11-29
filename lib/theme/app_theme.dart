import 'package:flutter/material.dart';
import 'app_colors.dart';
import 'app_text_styles.dart';

/// CarrotPlay Design System - Main Theme
/// Project Fresh Carrot
class AppTheme {
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      
      // Colors
      primaryColor: AppColors.carrotOrange,
      scaffoldBackgroundColor: AppColors.midnightBlack,
      colorScheme: const ColorScheme.dark(
        primary: AppColors.carrotOrange,
        secondary: AppColors.successGreen,
        surface: AppColors.surfaceDark,
        background: AppColors.midnightBlack,
        error: AppColors.errorRed,
        onPrimary: Colors.white,
        onSecondary: Colors.white,
        onSurface: Colors.white,
        onBackground: Colors.white,
        onError: Colors.white,
      ),

      // Typography
      fontFamily: AppTextStyles.fontFamily,
      textTheme: const TextTheme(
        displayLarge: AppTextStyles.header1,
        displayMedium: AppTextStyles.header2,
        displaySmall: AppTextStyles.header3,
        bodyLarge: AppTextStyles.bodyLarge,
        bodyMedium: AppTextStyles.bodyMedium,
        bodySmall: AppTextStyles.caption,
        labelSmall: AppTextStyles.label,
      ),

      // Component Themes
      iconTheme: const IconThemeData(
        color: AppColors.textPrimary,
        size: 24,
      ),
      
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.carrotOrange,
          foregroundColor: Colors.white,
          textStyle: AppTextStyles.button,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
      ),
      
      sliderTheme: SliderThemeData(
        activeTrackColor: AppColors.carrotOrange,
        inactiveTrackColor: AppColors.surfaceLight,
        thumbColor: Colors.white,
        overlayColor: AppColors.carrotOrange.withOpacity(0.2),
        trackHeight: 4.0,
      ),
    );
  }
}
