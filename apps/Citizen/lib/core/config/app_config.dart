/// Central app configuration for the CORE citizen app.
class AppConfig {
  AppConfig._();

  /// Runtime flag decided in main() after attempting Firebase init:
  ///
  /// true  -> Firebase is bypassed; registration/forgot-password use the
  ///         backend legacy mock OTP endpoints (/api/v1/otp/*) which require
  ///         mock OTP enabled on the backend (dev only).
  /// false -> Real Firebase Phone OTP flow. Android: google-services.json in
  ///         android/app/. Web: values in firebase_options.dart (already
  ///         filled for project `ai-based-graviance-system`).
  ///
  /// Falls back to true automatically when Firebase config is missing
  /// (e.g. google-services.json not added yet) so the app never crashes.
  static bool useFirebaseMock = false;

  /// Country code prepended to the 10-digit mobile number for Firebase (E.164).
  static const String countryCode = '+91';

  /// Languages supported by the backend (PreferredLanguage enum) — all 23.
  static const List<String> languages = [
    'ENGLISH', 'HINDI', 'BENGALI', 'TELUGU', 'MARATHI', 'TAMIL', 'URDU',
    'GUJARATI', 'KANNADA', 'ODIA', 'MALAYALAM', 'PUNJABI', 'ASSAMESE',
    'MAITHILI', 'SANSKRIT', 'KASHMIRI', 'NEPALI', 'KONKANI', 'SINDHI',
    'DOGRI', 'MANIPURI', 'BODO', 'SANTALI',
  ];
}
