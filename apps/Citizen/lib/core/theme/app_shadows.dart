import 'package:flutter/material.dart';

/// Elevation / shadow tokens from the Cognitive Trust Design System.
/// Four levels: surface (0), cards (1), overlays (2), modals/AI (3).
class AppShadows {
  AppShadows._();

  /// Level 0 — Surface: no shadow.
  static const List<BoxShadow> level0 = [];

  /// Level 1 — Cards & Panels: subtle shadow.
  static const List<BoxShadow> level1 = [
    BoxShadow(
      color: Color(0x0A000000),
      blurRadius: 4,
      offset: Offset(0, 1),
    ),
    BoxShadow(
      color: Color(0x05000000),
      blurRadius: 8,
      offset: Offset(0, 2),
    ),
  ];

  /// Level 2 — Overlays, dropdowns, sticky headers.
  static const List<BoxShadow> level2 = [
    BoxShadow(
      color: Color(0x0F000000),
      blurRadius: 8,
      offset: Offset(0, 4),
    ),
    BoxShadow(
      color: Color(0x08000000),
      blurRadius: 16,
      offset: Offset(0, 6),
    ),
  ];

  /// Level 3 — Modals & AI overlays (highest elevation).
  static const List<BoxShadow> level3 = [
    BoxShadow(
      color: Color(0x14000000),
      blurRadius: 16,
      offset: Offset(0, 8),
    ),
    BoxShadow(
      color: Color(0x0A000000),
      blurRadius: 32,
      offset: Offset(0, 16),
    ),
  ];
}
