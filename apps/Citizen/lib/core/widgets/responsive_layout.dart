import 'package:flutter/material.dart';

/// Responsive breakpoints for the CORE application.
/// Mobile:  < 600px
/// Tablet:  600px – 1024px
/// Desktop: > 1024px
class ResponsiveLayout extends StatelessWidget {
  /// The mobile layout (existing screens, unchanged).
  final Widget mobile;

  /// Optional tablet layout. Falls back to [desktop] if not provided.
  final Widget? tablet;

  /// The desktop layout.
  final Widget desktop;

  const ResponsiveLayout({
    super.key,
    required this.mobile,
    this.tablet,
    required this.desktop,
  });

  // ─── Breakpoint constants ───
  static const double mobileBreakpoint = 600;
  static const double desktopBreakpoint = 1024;

  /// Check if current width is desktop.
  static bool isDesktop(BuildContext context) =>
      MediaQuery.sizeOf(context).width >= desktopBreakpoint;

  /// Check if current width is tablet.
  static bool isTablet(BuildContext context) {
    final w = MediaQuery.sizeOf(context).width;
    return w >= mobileBreakpoint && w < desktopBreakpoint;
  }

  /// Check if current width is mobile.
  static bool isMobile(BuildContext context) =>
      MediaQuery.sizeOf(context).width < mobileBreakpoint;

  /// Returns the max content width for the current breakpoint.
  static double contentMaxWidth(BuildContext context) {
    if (isDesktop(context)) return 1100;
    if (isTablet(context)) return 720;
    return 480;
  }

  /// Returns horizontal padding appropriate for the current breakpoint.
  static double horizontalPadding(BuildContext context) {
    if (isDesktop(context)) return 40;
    if (isTablet(context)) return 24;
    return 16;
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth >= desktopBreakpoint) {
          return desktop;
        }
        if (constraints.maxWidth >= mobileBreakpoint) {
          return tablet ?? desktop;
        }
        return mobile;
      },
    );
  }
}
