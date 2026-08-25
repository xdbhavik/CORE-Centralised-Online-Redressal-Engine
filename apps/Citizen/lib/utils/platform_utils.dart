import 'dart:io';
import 'package:flutter/foundation.dart';

/// Centralised platform capability checks.
/// Use this everywhere instead of inline Platform.isX checks.
class PlatformUtils {
  PlatformUtils._();

  static bool get isWindows => !kIsWeb && Platform.isWindows;
  static bool get isAndroid => !kIsWeb && Platform.isAndroid;
  static bool get isIOS => !kIsWeb && Platform.isIOS;

  /// Camera capture is only supported natively on Android and iOS.
  static bool get supportsCameraCapture => isAndroid || isIOS;

  /// Gallery / file picking is supported on all three platforms.
  static bool get supportsGalleryPicker => true;

  /// Audio recording is supported on Android, iOS, and Windows.
  static bool get supportsAudioRecording => true;
}
