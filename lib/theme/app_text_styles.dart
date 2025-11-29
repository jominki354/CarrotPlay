import 'package:flutter/material.dart';
import 'app_colors.dart';

/// CarrotPlay Design System - Typography
/// Project Fresh Carrot
class AppTextStyles {
  AppTextStyles._();

  static const String fontFamily = 'Inter'; // or system default if not available

  // Headings
  static const TextStyle header1 = TextStyle(
    fontSize: 24,
    fontWeight: FontWeight.w700, // Bold
    color: AppColors.textPrimary,
    height: 1.2,
    letterSpacing: -0.5,
  );

  static const TextStyle header2 = TextStyle(
    fontSize: 20,
    fontWeight: FontWeight.w600, // SemiBold
    color: AppColors.textPrimary,
    height: 1.25,
    letterSpacing: -0.4,
  );

  static const TextStyle header3 = TextStyle(
    fontSize: 18,
    fontWeight: FontWeight.w600, // SemiBold
    color: AppColors.textPrimary,
    height: 1.3,
    letterSpacing: -0.3,
  );

  // Body Text
  static const TextStyle bodyLarge = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w500, // Medium
    color: AppColors.textPrimary,
    height: 1.5,
    letterSpacing: -0.2,
  );

  static const TextStyle bodyMedium = TextStyle(
    fontSize: 14,
    fontWeight: FontWeight.w400, // Regular
    color: AppColors.textPrimary,
    height: 1.5,
    letterSpacing: -0.1,
  );

  // Captions & Labels
  static const TextStyle caption = TextStyle(
    fontSize: 12,
    fontWeight: FontWeight.w400, // Regular
    color: AppColors.textSecondary,
    height: 1.4,
  );

  static const TextStyle label = TextStyle(
    fontSize: 10,
    fontWeight: FontWeight.w500, // Medium
    color: AppColors.textSecondary,
    height: 1.2,
    letterSpacing: 0.2,
  );
  
  // Button Text
  static const TextStyle button = TextStyle(
    fontSize: 16,
    fontWeight: FontWeight.w600, // SemiBold
    color: AppColors.textPrimary,
    height: 1.0,
  );
}
