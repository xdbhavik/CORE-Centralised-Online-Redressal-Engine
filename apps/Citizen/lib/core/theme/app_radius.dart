import 'package:flutter/material.dart';

/// Border radius tokens from the Cognitive Trust Design System.
class AppRadius {
  AppRadius._();

  /// 0px
  static const double none = 0;

  /// 4px (default)
  static const double sm = 4;

  /// 8px
  static const double md = 8;

  /// 12px
  static const double lg = 12;

  /// 16px
  static const double xl = 16;

  /// 9999px (pill / circle)
  static const double full = 9999;

  // ─── Pre-built BorderRadius objects ───

  static final BorderRadius borderRadiusNone = BorderRadius.circular(none);
  static final BorderRadius borderRadiusSm = BorderRadius.circular(sm);
  static final BorderRadius borderRadiusMd = BorderRadius.circular(md);
  static final BorderRadius borderRadiusLg = BorderRadius.circular(lg);
  static final BorderRadius borderRadiusXl = BorderRadius.circular(xl);
  static final BorderRadius borderRadiusXxl = BorderRadius.circular(24);
  static final BorderRadius borderRadiusFull = BorderRadius.circular(full);
}
