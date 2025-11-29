import 'package:flutter/material.dart';

/// CarrotPlay Design System - Color Palette
/// Project Fresh Carrot
class AppColors {
  // Private constructor
  AppColors._();

  // Primary Brand Colors
  static const Color carrotOrange = Color(0xFFFF6B00);
  static const Color midnightBlack = Color(0xFF000000);
  
  // Surface Colors (Glassmorphism bases)
  static const Color glassGrey = Color(0xFF1C1C1E);
  static const Color surfaceDark = Color(0xFF121212);
  static const Color surfaceLight = Color(0xFF2C2C2E);

  // Text Colors
  static const Color textPrimary = Color(0xFFFFFFFF);
  static const Color textSecondary = Color(0xFF8E8E93);
  static const Color textTertiary = Color(0xFF48484A);

  // Functional Colors
  static const Color successGreen = Color(0xFF34C759);
  static const Color errorRed = Color(0xFFFF453A);
  static const Color warningYellow = Color(0xFFFFD60A);
  static const Color infoBlue = Color(0xFF0A84FF);

  // Overlay Colors (for transparency)
  static final Color overlayDark = Colors.black.withOpacity(0.6);
  static final Color overlayLight = Colors.white.withOpacity(0.1);
  
  // Gradients
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [Color(0xFFFF8F40), Color(0xFFFF6B00)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}
