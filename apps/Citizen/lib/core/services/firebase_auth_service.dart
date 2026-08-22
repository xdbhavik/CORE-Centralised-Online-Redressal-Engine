import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart' show kIsWeb;

/// Wraps Firebase Phone Authentication (OTP via SMS) — works on Android AND web.
///
/// Mobile flow: verifyPhoneNumber → verificationId → PhoneAuthProvider.credential
/// Web flow:    signInWithPhoneNumber (invisible reCAPTCHA, plugin-managed)
///              → ConfirmationResult.confirm(otp)
///
/// Both are unified behind the same [sendOtp] / [verifyOtpAndGetIdToken] API,
/// so screens don't need platform checks.
class FirebaseAuthService {
  // Lazy: only touches FirebaseAuth when actually used, so the service is
  // safe to construct in mock mode and in widget tests (no Firebase init).
  FirebaseAuth? _authInstance;
  FirebaseAuth get _auth => _authInstance ??= FirebaseAuth.instance;

  /// Web-only: ConfirmationResult objects keyed by the synthetic verification
  /// id that [sendOtp] passes to its onCodeSent callback.
  static final Map<String, ConfirmationResult> _webConfirmations = {};
  static int _webSessionCounter = 0;

  /// Sends an OTP SMS to [phoneNumber] (must be E.164, e.g. +917057257037).
  Future<void> sendOtp({
    required String phoneNumber,
    required void Function(String verificationId, int? resendToken) onCodeSent,
    required void Function(String error) onError,
    required void Function(PhoneAuthCredential credential) onAutoVerified,
  }) async {
    if (kIsWeb) {
      await _sendOtpWeb(phoneNumber, onCodeSent, onError);
      return;
    }
    try {
      await _auth.verifyPhoneNumber(
        phoneNumber: phoneNumber,
        timeout: const Duration(seconds: 60),
        verificationCompleted: onAutoVerified,
        verificationFailed: (FirebaseAuthException e) =>
            onError('[${e.code}] ${e.message ?? 'Phone verification failed'}'),
        codeSent: (String verificationId, int? resendToken) =>
            onCodeSent(verificationId, resendToken),
        codeAutoRetrievalTimeout: (_) {},
      );
    } catch (e) {
      onError(e.toString());
    }
  }

  /// Web OTP send: signInWithPhoneNumber automatically renders an invisible
  /// reCAPTCHA (plugin-managed) and returns a ConfirmationResult which we
  /// keep until the user enters the OTP.
  Future<void> _sendOtpWeb(
    String phoneNumber,
    void Function(String verificationId, int? resendToken) onCodeSent,
    void Function(String error) onError,
  ) async {
    try {
      final confirmation = await _auth.signInWithPhoneNumber(phoneNumber);
      final sessionId =
          'web-${DateTime.now().millisecondsSinceEpoch}-${_webSessionCounter++}';
      _webConfirmations[sessionId] = confirmation;
      onCodeSent(sessionId, null);
    } on FirebaseAuthException catch (e) {
      onError('[${e.code}] ${e.message ?? 'Phone verification failed'}');
    } catch (e) {
      onError(e.toString());
    }
  }

  /// Verifies the user-entered OTP and returns a Firebase ID token,
  /// which the backend verifies with the Firebase Admin SDK.
  Future<String> verifyOtpAndGetIdToken({
    required String verificationId,
    required String otp,
  }) async {
    if (kIsWeb) {
      final confirmation = _webConfirmations.remove(verificationId);
      if (confirmation == null) {
        throw FirebaseAuthException(
          code: 'expired-session',
          message: 'OTP session expired. Please resend the OTP.',
        );
      }
      final userCred = await confirmation.confirm(otp);
      final idToken = await userCred.user?.getIdToken();
      if (idToken == null) {
        throw FirebaseAuthException(
            code: 'no-id-token',
            message: 'Could not obtain Firebase ID token');
      }
      return idToken;
    }
    final credential = PhoneAuthProvider.credential(
      verificationId: verificationId,
      smsCode: otp,
    );
    return getIdTokenFromCredential(credential);
  }

  /// Signs in with a credential (manual or auto-retrieved) and returns the ID token.
  Future<String> getIdTokenFromCredential(PhoneAuthCredential credential) async {
    final userCred = await _auth.signInWithCredential(credential);
    final idToken = await userCred.user?.getIdToken();
    if (idToken == null) {
      throw FirebaseAuthException(
          code: 'no-id-token', message: 'Could not obtain Firebase ID token');
    }
    return idToken;
  }

  Future<void> signOut() => _auth.signOut();
}
