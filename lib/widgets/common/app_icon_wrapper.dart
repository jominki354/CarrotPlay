import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../../theme/app_colors.dart';
import '../../theme/app_dimens.dart';

/// A wrapper for app icons that applies the squircle shape and standard styling.
/// Project Fresh Carrot
class AppIconWrapper extends StatelessWidget {
  final Uint8List? iconData;
  final double size;
  final double radius;

  const AppIconWrapper({
    super.key,
    this.iconData,
    this.size = 56.0,
    this.radius = AppDimens.radiusMedium,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: iconData == null ? Colors.white.withOpacity(0.1) : Colors.transparent,
        borderRadius: BorderRadius.circular(radius),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.2),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(radius),
        child: iconData != null
            ? Image.memory(
                iconData!,
                fit: BoxFit.cover,
                gaplessPlayback: true,
              )
            : Icon(
                Icons.android_rounded,
                color: Colors.white.withOpacity(0.5),
                size: size * 0.5,
              ),
      ),
    );
  }
}
