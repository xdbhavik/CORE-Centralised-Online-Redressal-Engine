import 'package:flutter/material.dart';

/// Centralized animation durations and curves based on Stitch Design System specifications.
class AppAnimations {
  AppAnimations._();

  // ─── Durations ───

  /// 300ms for page-to-page navigation transitions
  static const Duration pageTransition = Duration(milliseconds: 300);

  /// 250ms for opening modals, bottom sheets, and dialogs
  static const Duration modalOpen = Duration(milliseconds: 250);

  /// 200ms for closing modals, bottom sheets, and dialogs
  static const Duration modalClose = Duration(milliseconds: 200);

  /// 100ms for button press scale down / color shift
  static const Duration buttonPress = Duration(milliseconds: 100);

  /// 350ms for cards entering the screen
  static const Duration cardEntrance = Duration(milliseconds: 350);

  /// 50ms stagger delay between list items entering
  static const Duration staggerDelay = Duration(milliseconds: 50);

  /// 300ms for toasts/snackbars sliding in
  static const Duration toastSlide = Duration(milliseconds: 300);

  /// 800ms for animated counters counting up
  static const Duration counterAnim = Duration(milliseconds: 800);
  
  /// 1200ms for AI / waveform continuous pulse animations
  static const Duration waveformPulse = Duration(milliseconds: 1200);

  // ─── Curves ───

  /// easeInOutCubic for page transitions
  static const Curve pageCurve = Curves.easeInOutCubic;

  /// easeOutCubic for modal entrances
  static const Curve modalOpenCurve = Curves.easeOutCubic;

  /// easeInCubic for modal exits
  static const Curve modalCloseCurve = Curves.easeInCubic;

  /// easeIn for button press state
  static const Curve buttonPressCurve = Curves.easeIn;

  /// easeOutQuart for card entrances
  static const Curve cardEntranceCurve = Curves.easeOutQuart;

  /// easeOutBack for toast slide-ins to add a slight bounce
  static const Curve toastCurve = Curves.easeOutBack;

  /// easeOut for counters
  static const Curve counterCurve = Curves.easeOut;
}
