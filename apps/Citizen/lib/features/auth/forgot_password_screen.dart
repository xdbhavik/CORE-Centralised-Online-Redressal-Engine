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

/// Forgot Password — real 3-step OTP flow against the backend:
///   1. Enter registered mobile  → POST /otp/send     (purpose: PASSWORD_RESET)
///   2. Enter 6-digit OTP        → POST /otp/verify   (purpose: PASSWORD_RESET)
///   3. Enter new password       → POST /otp/reset-password
class ForgotPasswordScreen extends StatefulWidget {
  const ForgotPasswordScreen({super.key});

  @override
  State<ForgotPasswordScreen> createState() => _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends State<ForgotPasswordScreen> {
  final OtpService _otpService = OtpService();
  final FirebaseAuthService _firebaseAuth = FirebaseAuthService();

  // Real Firebase mode state
  String? _verificationId;
  String? _idToken;

  // Step controllers
  final _mobileController = TextEditingController();
  final List<TextEditingController> _otpControllers =
      List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _otpFocusNodes = List.generate(6, (_) => FocusNode());
  final _passwordController = TextEditingController();
  final _confirmController = TextEditingController();

  int _step = 0; // 0 = mobile, 1 = otp, 2 = new password, 3 = success
  bool _isLoading = false;
  String? _errorText;

  // Resend cooldown — matches backend otp.resend-cooldown-seconds=60
  static const int _resendSeconds = 60;
  int _timeLeft = 0;
  Timer? _timer;

  String get _mobile => _mobileController.text.trim();
  String get _otp => _otpControllers.map((c) => c.text).join();

  @override
  void dispose() {
    _timer?.cancel();
    _mobileController.dispose();
    _passwordController.dispose();
    _confirmController.dispose();
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
          // Android auto-retrieval — skip straight to password step
          try {
            final idToken =
                await _firebaseAuth.getIdTokenFromCredential(credential);
            if (!mounted) return;
            setState(() {
              _isLoading = false;
              _idToken = idToken;
              _step = 2;
            });
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

    // ── DEV MOCK MODE: skip OTP — backend dev-mock accepts mock-<mobile>
    // idToken at /auth/firebase/reset-password. Jump straight to password step.
    setState(() {
      _isLoading = false;
      _idToken = 'mock-$mobile';
      _step = 2;
    });
  }

  // ── Step 2: verify OTP ────────────────────────────────────────────────────
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
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _idToken = idToken;
          _step = 2;
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
      final resp = await _otpService.verifyOtp(_mobile, _otp, 'PASSWORD_RESET');
      if (!mounted) return;
      if (resp.success == false) {
        setState(() {
          _isLoading = false;
          _errorText = resp.message ?? 'Incorrect code. Please try again.';
        });
        return;
      }
      setState(() {
        _isLoading = false;
        _step = 2;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorText = _backendError(e, 'Incorrect code. Please try again.');
      });
    }
  }

  // ── Step 3: reset password ────────────────────────────────────────────────
  Future<void> _handleResetPassword() async {
    final password = _passwordController.text;
    final confirm = _confirmController.text;

    if (password.length < 6) {
      setState(() => _errorText = 'Password must be at least 6 characters.');
      return;
    }
    if (password != confirm) {
      setState(() => _errorText = 'Passwords do not match.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    // ── REAL FIREBASE MODE ──
    if (!AppConfig.useFirebaseMock) {
      try {
        final success = await AuthService().resetPasswordWithFirebase(
          idToken: _idToken!,
          newPassword: password,
        );
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          if (success) {
            _step = 3;
          } else {
            _errorText = 'Password reset failed. Try again.';
          }
        });
      } catch (e) {
        if (!mounted) return;
        setState(() {
          _isLoading = false;
          _errorText = _backendError(e, 'Password reset failed. Try again.');
        });
      }
      return;
    }

    // ── DEV MOCK MODE: same endpoint, mock idToken set in step 1 ──
    try {
      final success = await AuthService().resetPasswordWithFirebase(
        idToken: _idToken!,
        newPassword: password,
      );
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        if (success) {
          _step = 3;
        } else {
          _errorText = 'Password reset failed. Try again.';
        }
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorText = _backendError(e, 'Password reset failed. Try again.');
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
      await _otpService.sendOtp(_mobile, 'PASSWORD_RESET');
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: ResponsiveLayout(
          mobile: _buildMobile(context),
          desktop: _buildDesktop(context),
        ),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints:
            const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
        child: Column(
          children: [
            _buildHeader(context),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space4,
                    vertical: AppSpacing.space4),
                child: _buildStepContent(),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1200),
        child: Row(
          children: [
            // Left branding column
            Expanded(
              flex: 11,
              child: Container(
                margin: const EdgeInsets.all(AppSpacing.space4),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(32),
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                    colors: [
                      AppColors.primaryContainer.withValues(alpha: 0.1),
                      AppColors.primaryContainer.withValues(alpha: 0.3),
                    ],
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.space10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back,
                            color: AppColors.onSurfaceVariant),
                        onPressed: () => _handleBack(context),
                      ),
                      const Spacer(),
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: const Icon(Icons.lock_reset,
                            size: 48, color: AppColors.primary),
                      ),
                      const SizedBox(height: AppSpacing.space6),
                      Text('Reset Password',
                          style: textTheme.displayLarge
                              ?.copyWith(color: AppColors.onSurface)),
                      const SizedBox(height: AppSpacing.space3),
                      Text(
                        _stepSubtitle,
                        style: textTheme.bodyLarge
                            ?.copyWith(color: AppColors.onSurfaceVariant),
                      ),
                      const Spacer(),
                    ],
                  ),
                ),
              ),
            ),
            // Right form column
            Expanded(
              flex: 9,
              child: Padding(
                padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space10),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: SingleChildScrollView(child: _buildStepContent()),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String get _stepSubtitle => switch (_step) {
        0 => 'Enter your registered mobile number to receive a verification code.',
        1 => 'Enter the 6-digit code sent to +91 $_mobile.',
        2 => 'Choose a new password for your account.',
        _ => 'Your password has been reset successfully.',
      };

  void _handleBack(BuildContext context) {
    if (_step == 1 || _step == 2) {
      // Allow going back a step (OTP expired / wrong number)
      setState(() {
        _step -= 1;
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

  Widget _buildHeader(BuildContext context) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.8),
      ),
      child: Row(
        children: [
          IconButton(
            icon:
                const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
            onPressed: () => _handleBack(context),
          ),
          const SizedBox(width: 8),
          Text('Forgot Password',
              style: AppTypography.titleLarge
                  .copyWith(color: AppColors.onSurface)),
        ],
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
            child: Icon(
              _step == 3 ? Icons.check_circle : Icons.lock_reset,
              color: _step == 3 ? AppColors.secondary : AppColors.primary,
              size: 32,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.space4),
        Center(
          child: Text(
            _step == 3 ? 'Password Reset!' : 'Reset Password',
            style: AppTypography.headlineMedium
                .copyWith(color: AppColors.onSurface),
          ),
        ),
        const SizedBox(height: AppSpacing.space2),
        Center(
          child: Text(
            _stepSubtitle,
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
            child: Text(
              _errorText!,
              style: AppTypography.labelSmall
                  .copyWith(color: AppColors.onErrorContainer),
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
        ],

        if (_step == 0) ..._buildMobileStep(),
        if (_step == 1) ..._buildOtpStep(),
        if (_step == 2) ..._buildPasswordStep(),
        if (_step == 3) ..._buildSuccessStep(),
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
        onChanged: (_) => setState(() => _errorText = null),
      ),
      const SizedBox(height: AppSpacing.space4),
      CoreButton(
        label: 'Send OTP',
        variant: CoreButtonVariant.primary,
        isLoading: _isLoading,
        onPressed: _handleSendOtp,
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
        label: 'Verify Code',
        variant: CoreButtonVariant.primary,
        isLoading: _isLoading,
        onPressed: _handleVerifyOtp,
      ),
      const SizedBox(height: AppSpacing.space3),
      Center(
        child: GestureDetector(
          onTap: _timeLeft == 0 ? _handleResend : null,
          child: Text(
            _timeLeft > 0
                ? 'Resend code in ${_timeLeft}s'
                : 'Resend code',
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

  List<Widget> _buildPasswordStep() {
    return [
      CoreTextField(
        controller: _passwordController,
        label: 'New Password',
        hintText: 'Minimum 6 characters',
        isPassword: true,
        prefixIcon: Icons.lock_outline,
        onChanged: (_) => setState(() => _errorText = null),
      ),
      const SizedBox(height: AppSpacing.space3),
      CoreTextField(
        controller: _confirmController,
        label: 'Confirm New Password',
        isPassword: true,
        prefixIcon: Icons.lock_outline,
        onChanged: (_) => setState(() => _errorText = null),
      ),
      const SizedBox(height: AppSpacing.space4),
      CoreButton(
        label: 'Reset Password',
        variant: CoreButtonVariant.primary,
        isLoading: _isLoading,
        onPressed: _handleResetPassword,
      ),
    ];
  }

  List<Widget> _buildSuccessStep() {
    return [
      Text(
        'You can now sign in with your new password.',
        style:
            AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
        textAlign: TextAlign.center,
      ),
      const SizedBox(height: AppSpacing.space4),
      CoreButton(
        label: 'Back to Sign In',
        variant: CoreButtonVariant.primary,
        onPressed: () => context.go('/login'),
      ),
    ];
  }
}
