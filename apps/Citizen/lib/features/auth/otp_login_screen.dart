import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import '../../core/config/app_config.dart';
import '../../core/services/auth_service.dart';
import '../../core/services/firebase_auth_service.dart';
import '../../core/services/otp_service.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/core_text_field.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/staggered_entrance.dart';

/// OTP-only (passwordless) login for citizens.
///
/// Step 1: enter registered mobile → Firebase sends OTP SMS
///         (Android: Play Integrity / Web: invisible reCAPTCHA).
/// Step 2: enter 6-digit code → Firebase ID token → POST /auth/firebase/login
///         → JWT tokens → Home.
class OtpLoginScreen extends StatefulWidget {
  const OtpLoginScreen({super.key});

  @override
  State<OtpLoginScreen> createState() => _OtpLoginScreenState();
}

class _OtpLoginScreenState extends State<OtpLoginScreen> {
  final OtpService _otpService = OtpService();
  final FirebaseAuthService _firebaseAuth = FirebaseAuthService();
  final AuthService _authService = AuthService();

  String? _verificationId;

  final _mobileController = TextEditingController();
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());

  int _step = 0; // 0 = mobile, 1 = otp
  bool _isLoading = false;
  String? _errorText;

  static const int _resendSeconds = 60;
  int _timeLeft = 0;
  Timer? _timer;

  String get _mobile => _mobileController.text.trim();
  String get _otp => _otpControllers.map((c) => c.text).join();

  @override
  void dispose() {
    _timer?.cancel();
    _mobileController.dispose();
    for (final c in _otpControllers) {
      c.dispose();
    }
    for (final n in _otpFocusNodes) {
      n.dispose();
    }
    super.dispose();
  }

  void _startResendTimer() {
    _timer?.cancel();
    setState(() => _timeLeft = _resendSeconds);
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_timeLeft > 0) {
        setState(() => _timeLeft--);
      } else {
        timer.cancel();
      }
    });
  }

  String _backendError(Object e, String fallback) {
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map && data['message'] != null) {
        return data['message'].toString();
      }
    }
    return fallback;
  }

  /// Shared success path — exchange the Firebase ID token for app JWT tokens.
  Future<void> _loginWithIdToken(String idToken) async {
    final success = await _authService.loginWithFirebase(idToken);
    if (!mounted) return;
    setState(() => _isLoading = false);
    if (success) {
      context.go('/home');
    } else {
      setState(() => _errorText =
          'No account found for this number. Please register first.');
    }
  }

  // ── Step 1: send OTP ──────────────────────────────────────────────────────
  Future<void> _handleSendOtp() async {
    final mobile = _mobile;
    if (mobile.length != 10 || int.tryParse(mobile) == null) {
      setState(() => _errorText = 'Enter a valid 10-digit mobile number.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    // ── REAL FIREBASE MODE ──
    if (!AppConfig.useFirebaseMock) {
      await _firebaseAuth.sendOtp(
        phoneNumber: '${AppConfig.countryCode}$mobile',
        onCodeSent: (verificationId, _) {
          if (!mounted) return;
          setState(() {
            _isLoading = false;
            _verificationId = verificationId;
            _step = 1;
          });
          _startResendTimer();
        },
        onAutoVerified: (credential) async {
          // Android auto-retrieval — log in without typing the OTP
          try {
            final idToken =
                await _firebaseAuth.getIdTokenFromCredential(credential);
            await _loginWithIdToken(idToken);
          } catch (e) {
            if (mounted) {
              setState(() {
                _isLoading = false;
                _errorText = 'Auto-verification failed. Enter the OTP manually.';
              });
            }
          }
        },
        onError: (error) {
          if (!mounted) return;
          setState(() {
            _isLoading = false;
            _errorText = error;
          });
        },
      );
      return;
    }

    // ── DEV MOCK MODE: skip OTP — backend dev-mock accepts mock-<mobile> ──
    try {
      await _loginWithIdToken('mock-$mobile');
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorText = _backendError(e, 'Login failed. Try again.');
      });
    }
  }

  // ── Step 2: verify OTP & login ────────────────────────────────────────────
  Future<void> _handleVerifyOtp() async {
    if (_otp.length < 6) {
      setState(() => _errorText = 'Enter the 6-digit code.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    // ── REAL FIREBASE MODE ──
    if (!AppConfig.useFirebaseMock) {
      try {
        final idToken = await _firebaseAuth.verifyOtpAndGetIdToken(
          verificationId: _verificationId!,
          otp: _otp,
        );
        await _loginWithIdToken(idToken);
      } on DioException catch (e) {
        // Backend rejected the login (e.g. no account for this number) —
        // show the real reason, not "incorrect code".
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _errorText = _backendError(e, 'Login failed. Please try again.');
        });
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _errorText = 'Incorrect code. Please try again.';
        });
      }
      return;
    }

    // ── DEV MOCK MODE ──
    try {
      final resp = await _otpService.verifyOtp(_mobile, _otp, 'LOGIN');
      if (!mounted) return;
      if (resp.success == false) {
        setState(() {
          _isLoading = false;
          _errorText = resp.message ?? 'Incorrect code. Please try again.';
        });
        return;
      }
      // Backend dev-mock accepts mock-<mobile> as a Firebase ID token.
      await _loginWithIdToken('mock-$_mobile');
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorText = _backendError(e, 'Incorrect code. Please try again.');
      });
    }
  }

  Future<void> _handleResend() async {
    if (_timeLeft > 0) return;
    // ── REAL FIREBASE MODE ──
    if (!AppConfig.useFirebaseMock) {
      await _firebaseAuth.sendOtp(
        phoneNumber: '${AppConfig.countryCode}$_mobile',
        onCodeSent: (verificationId, _) {
          if (!mounted) return;
          setState(() {
            _verificationId = verificationId;
            for (final c in _otpControllers) {
              c.clear();
            }
          });
          _startResendTimer();
        },
        onAutoVerified: (_) {},
        onError: (error) {
          if (mounted) setState(() => _errorText = error);
        },
      );
      return;
    }
    // ── DEV MOCK MODE ──
    try {
      await _otpService.sendOtp(_mobile, 'LOGIN');
      if (!mounted) return;
      for (final c in _otpControllers) {
        c.clear();
      }
      _startResendTimer();
    } catch (e) {
      if (!mounted) return;
      setState(() => _errorText = _backendError(e, 'Could not resend OTP.'));
    }
  }

  void _handleBack() {
    if (_step == 1) {
      setState(() {
        _step = 0;
        _errorText = null;
      });
      return;
    }
    if (GoRouter.of(context).canPop()) {
      context.pop();
    } else {
      context.go('/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface.withValues(alpha: 0.8),
        elevation: 0,
        surfaceTintColor: Colors.transparent,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
          onPressed: _handleBack,
        ),
        title: Text('Login with OTP',
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.onSurface,
            )),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints:
                const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.space4,
                vertical: AppSpacing.space4,
              ),
              child: _buildStepContent(),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStepContent() {
    return StaggeredEntrance(
      children: [
        Center(
          child: Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: AppColors.primaryContainer.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Icon(Icons.sms_outlined,
                color: AppColors.primary, size: 32),
          ),
        ),
        const SizedBox(height: AppSpacing.space4),
        Center(
          child: Text(
            'Passwordless Sign In',
            style: AppTypography.headlineMedium
                .copyWith(color: AppColors.onSurface),
          ),
        ),
        const SizedBox(height: AppSpacing.space2),
        Center(
          child: Text(
            _step == 0
                ? 'Enter your registered mobile number. We will send you a one-time code — no password needed.'
                : 'Enter the 6-digit code sent to ${AppConfig.countryCode} $_mobile.',
            style: AppTypography.bodyMedium
                .copyWith(color: AppColors.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: AppSpacing.space6),

        if (_errorText != null) ...[
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.errorContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                const Icon(Icons.error_outline,
                    size: 18, color: AppColors.onErrorContainer),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _errorText!,
                    style: AppTypography.labelSmall
                        .copyWith(color: AppColors.onErrorContainer),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
        ],

        if (_step == 0) ..._buildMobileStep(),
        if (_step == 1) ..._buildOtpStep(),
      ],
    );
  }

  List<Widget> _buildMobileStep() {
    return [
      CoreTextField(
        controller: _mobileController,
        label: 'Registered Mobile Number',
        hintText: '10-digit mobile number',
        prefixIcon: Icons.phone_android,
        keyboardType: TextInputType.phone,
        maxLength: 10,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly],
        autofillHints: const [AutofillHints.telephoneNumber],
        textInputAction: TextInputAction.done,
        onChanged: (_) {
          if (_errorText != null) setState(() => _errorText = null);
        },
        onSubmitted: (_) => _handleSendOtp(),
      ),
      const SizedBox(height: AppSpacing.space4),
      CoreButton(
        label: 'Send OTP',
        variant: CoreButtonVariant.primary,
        isLoading: _isLoading,
        onPressed: _handleSendOtp,
      ),
      const SizedBox(height: AppSpacing.space3),
      Center(
        child: TextButton.icon(
          onPressed: () => context.pop(),
          icon: const Icon(Icons.lock_outline, size: 16),
          label: Text('Sign in with password instead',
              style:
                  AppTypography.labelSmall.copyWith(color: AppColors.primary)),
        ),
      ),
    ];
  }

  List<Widget> _buildOtpStep() {
    return [
      Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: List.generate(6, (i) {
          return SizedBox(
            width: 46,
            child: TextField(
              controller: _otpControllers[i],
              focusNode: _otpFocusNodes[i],
              keyboardType: TextInputType.number,
              textAlign: TextAlign.center,
              maxLength: 1,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              autofillHints: const [AutofillHints.oneTimeCode],
              style: AppTypography.titleLarge
                  .copyWith(color: AppColors.onSurface),
              decoration: InputDecoration(
                counterText: '',
                filled: true,
                fillColor: AppColors.surfaceContainerLowest,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide:
                      const BorderSide(color: AppColors.outlineVariant),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide:
                      const BorderSide(color: AppColors.primary, width: 2),
                ),
              ),
              onChanged: (value) {
                setState(() => _errorText = null);
                if (value.isNotEmpty && i < 5) {
                  _otpFocusNodes[i + 1].requestFocus();
                } else if (value.isEmpty && i > 0) {
                  _otpFocusNodes[i - 1].requestFocus();
                }
                if (_otp.length == 6) _handleVerifyOtp();
              },
            ),
          );
        }),
      ),
      const SizedBox(height: AppSpacing.space4),
      CoreButton(
        label: 'Verify & Sign In',
        variant: CoreButtonVariant.primary,
        isLoading: _isLoading,
        onPressed: _handleVerifyOtp,
      ),
      const SizedBox(height: AppSpacing.space3),
      Center(
        child: GestureDetector(
          onTap: _timeLeft == 0 ? _handleResend : null,
          child: Text(
            _timeLeft > 0 ? 'Resend code in ${_timeLeft}s' : 'Resend code',
            style: AppTypography.labelSmall.copyWith(
              color: _timeLeft > 0
                  ? AppColors.onSurfaceVariant
                  : AppColors.primary,
            ),
          ),
        ),
      ),
    ];
  }
}
