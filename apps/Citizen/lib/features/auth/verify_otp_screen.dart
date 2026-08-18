import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'dart:async';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/staggered_entrance.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/theme/app_radius.dart';
import '../../core/theme/app_shadows.dart';

import '../../core/services/auth_service.dart';
import '../../core/services/firebase_auth_service.dart';

class VerifyOtpScreen extends StatefulWidget {
  final String contact;
  final Map<String, dynamic>? registrationData;
  
  const VerifyOtpScreen({
    super.key, 
    this.contact = '+1 ••• ••• 4492',
    this.registrationData,
  });

  @override
  State<VerifyOtpScreen> createState() => _VerifyOtpScreenState();
}

class _VerifyOtpScreenState extends State<VerifyOtpScreen> with SingleTickerProviderStateMixin {
  final List<TextEditingController> _controllers = List.generate(6, (_) => TextEditingController());
  final List<FocusNode> _focusNodes = List.generate(6, (_) => FocusNode());
  
  late final AnimationController _pulseController;
  late final Animation<double> _pulseAnimation;
  
  bool _isLoading = false;
  String? _errorText;
  // Matches backend otp.resend-cooldown-seconds=60
  int _timeLeft = 60;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();
    
    _pulseAnimation = Tween<double>(begin: 0.8, end: 1.5).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeOut),
    );

    _startTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _pulseController.dispose();
    for (var controller in _controllers) {
      controller.dispose();
    }
    for (var node in _focusNodes) {
      node.dispose();
    }
    super.dispose();
  }

  void _startTimer() {
    setState(() => _timeLeft = 60);
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_timeLeft > 0) {
        setState(() => _timeLeft--);
      } else {
        timer.cancel();
      }
    });
  }

  void _handleVerify() async {
    final otp = _controllers.map((c) => c.text).join();
    if (otp.length < 6) return;

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    final authService = AuthService();
    final reg = widget.registrationData;
    final verificationId = reg?['verificationId'] as String?;

    // ── REAL FIREBASE MODE: verificationId present → verify with Firebase ──
    if (verificationId != null && reg != null) {
      try {
        final idToken = await FirebaseAuthService()
            .verifyOtpAndGetIdToken(verificationId: verificationId, otp: otp);
        final success = await authService.registerWithFirebase(
          idToken: idToken,
          name: reg['name'],
          email: reg['email'] ?? '',
          password: reg['password'],
          preferredLanguage: reg['preferredLanguage'],
        );
        if (mounted) {
          setState(() => _isLoading = false);
          if (success) {
            context.go('/home');
          } else {
            setState(() =>
                _errorText = 'Registration failed. Please try again.');
          }
        }
      } catch (e) {
        if (mounted) {
          setState(() {
            _isLoading = false;
            _errorText = 'Incorrect code. Please try again.';
          });
        }
      }
      return;
    }

    // ── DEV MOCK MODE: backend legacy OTP verify ──
    final isVerified = await authService.verifyOtp(widget.contact, otp, purpose: "REGISTRATION");

    if (!isVerified) {
      if (mounted) {
        setState(() {
          _isLoading = false;
          _errorText = 'Incorrect code. Please try again.';
        });
      }
      return;
    }

    if (reg != null) {
      final success = await authService.register(
        reg['name'], 
        reg['mobile'], 
        reg['email'], 
        reg['password'], 
        reg['preferredLanguage']
      );
      
      if (mounted) {
        setState(() => _isLoading = false);
        if (success) {
          context.go('/home');
        } else {
          setState(() => _errorText = 'Registration failed. Please try again.');
        }
      }
    } else {
      if (mounted) {
        setState(() => _isLoading = false);
        context.go('/home');
      }
    }
  }

  /// Resend OTP — real mode re-calls Firebase, mock mode re-calls backend.
  void _handleResend() {
    if (_timeLeft > 0) return;
    final reg = widget.registrationData;
    final mobile = reg?['mobile'] as String? ?? widget.contact;

    if (reg?['verificationId'] != null) {
      // Real Firebase resend — new verificationId replaces the old one
      FirebaseAuthService().sendOtp(
        phoneNumber: mobile.startsWith('+') ? mobile : '+91$mobile',
        onCodeSent: (newVerificationId, _) {
          if (mounted) {
            setState(() => reg!['verificationId'] = newVerificationId);
            _startTimer();
          }
        },
        onAutoVerified: (_) {},
        onError: (error) {
          if (mounted) setState(() => _errorText = error);
        },
      );
    } else {
      AuthService().sendOtp(mobile, purpose: "REGISTRATION");
      _startTimer();
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
        constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
        child: Column(
          children: [
            _buildHeader(context),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space4, vertical: AppSpacing.space4),
                child: _buildFormContent(context),
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
            // ── Left Column (Branding) ──
            Expanded(
              flex: 11, // 55%
              child: Container(
                margin: const EdgeInsets.all(AppSpacing.space4),
                decoration: BoxDecoration(
                  borderRadius: AppRadius.borderRadiusXxl,
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
                        icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
                        onPressed: () {
                          if (GoRouter.of(context).canPop()) {
                            context.pop();
                          } else {
                            context.go('/login');
                          }
                        },
                      ),
                      const Spacer(),
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: AppRadius.borderRadiusXl,
                          boxShadow: AppShadows.level2,
                        ),
                        child: const Icon(
                          Icons.verified_user,
                          size: 48,
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space6),
                      Text(
                        'Verify your identity.',
                        style: textTheme.displayLarge?.copyWith(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space3),
                      Text(
                        'Protecting your account with two-factor authentication.',
                        style: textTheme.bodyLarge?.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                      const Spacer(),
                    ],
                  ),
                ),
              ),
            ),

            // ── Right Column (Form) ──
            Expanded(
              flex: 9, // 45%
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space10),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: SingleChildScrollView(
                      child: _buildFormContent(context, isDesktop: true),
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFormContent(BuildContext context, {bool isDesktop = false}) {
    return StaggeredEntrance(
      children: [
        if (!isDesktop) ...[
          const SizedBox(height: AppSpacing.space6),
          // Animated Lock Icon
          Center(
            child: Stack(
              alignment: Alignment.center,
              children: [
                AnimatedBuilder(
                  animation: _pulseAnimation,
                  builder: (context, child) {
                    return Opacity(
                      opacity: 1.0 - (_pulseController.value),
                      child: Transform.scale(
                        scale: _pulseAnimation.value,
                        child: Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            color: AppColors.primaryContainer.withValues(alpha: 0.3),
                            shape: BoxShape.circle,
                          ),
                        ),
                      ),
                    );
                  },
                ),
                Container(
                  width: 64,
                  height: 64,
                  decoration: const BoxDecoration(
                    color: AppColors.primaryContainer,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.lock, color: AppColors.onPrimaryContainer, size: 32),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.space4),
          Center(child: Text('Verify your identity', style: AppTypography.displayLargeMobile.copyWith(color: AppColors.onSurface))),
          const SizedBox(height: AppSpacing.space1),
        ],
        Center(
          child: Text(
            "We've sent a 6-digit code to",
            style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
        ),
        Center(
          child: Text(
            widget.contact,
            style: AppTypography.labelSmall.copyWith(color: AppColors.onSurface),
            textAlign: TextAlign.center,
          ),
        ),
        const SizedBox(height: AppSpacing.space6),
                        
                        // OTP Inputs
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: List.generate(6, (index) => _buildOtpDigit(index)),
                        ),
                        
                        if (_errorText != null) ...[
                          const SizedBox(height: AppSpacing.space3),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.error, color: AppColors.error, size: 16),
                              const SizedBox(width: 4),
                              Text(_errorText!, style: AppTypography.caption.copyWith(color: AppColors.error)),
                            ],
                          )
                        ],
                        
                        const SizedBox(height: AppSpacing.space6),
                        
                        // Resend timer
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              'Resend code in ',
                              style: AppTypography.labelSmall.copyWith(color: AppColors.onSurfaceVariant),
                            ),
                            Text(
                              '0:${_timeLeft.toString().padLeft(2, '0')}',
                              style: AppTypography.titleLarge.copyWith(color: AppColors.primary),
                            ),
                          ],
                        ),
                        
                        const SizedBox(height: AppSpacing.space2),
                        
                        // Resend button
                        Center(
                          child: InkWell(
                            onTap: _timeLeft == 0 ? _handleResend : null,
                            borderRadius: BorderRadius.circular(24),
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                              decoration: BoxDecoration(
                                color: _timeLeft == 0 ? AppColors.primaryContainer.withValues(alpha: 0.5) : Colors.transparent,
                                borderRadius: BorderRadius.circular(24),
                              ),
                              child: Text(
                                'Resend SMS',
                                style: AppTypography.labelSmall.copyWith(
                                  color: _timeLeft == 0 ? AppColors.primary : AppColors.onSurfaceVariant.withValues(alpha: 0.5),
                                ),
                              ),
                            ),
                          ),
                        ),
                        
                        const SizedBox(height: AppSpacing.space6),
                        
                        CoreButton(
                          label: 'Verify',
                          variant: CoreButtonVariant.primary,
                          isLoading: _isLoading,
                          onPressed: _handleVerify,
                        ),
      ],
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.8),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
            onPressed: () {
              if (GoRouter.of(context).canPop()) {
                context.pop();
              } else {
                context.go('/login');
              }
            },
          ),
          Text('Otp Verification', style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface)),
          Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(right: 8),
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.person, color: AppColors.onPrimary, size: 18),
          ),
        ],
      ),
    );
  }

  Widget _buildOtpDigit(int index) {
    final bool hasError = _errorText != null;
    return SizedBox(
      width: 48,
      height: 56,
      child: Focus(
        onFocusChange: (hasFocus) {
          setState(() {}); // Rebuild for focus styles if needed
        },
        child: TextField(
          controller: _controllers[index],
          focusNode: _focusNodes[index],
          textAlign: TextAlign.center,
          style: AppTypography.headlineMedium.copyWith(
            color: _focusNodes[index].hasFocus ? AppColors.onPrimaryFixedVariant : AppColors.onSurface,
          ),
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            LengthLimitingTextInputFormatter(1),
          ],
          decoration: InputDecoration(
            filled: true,
            fillColor: hasError 
                ? AppColors.errorContainer 
                : (_focusNodes[index].hasFocus ? AppColors.primaryFixed : AppColors.surfaceContainer),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(8),
              borderSide: BorderSide.none,
            ),
            counterText: "",
            contentPadding: EdgeInsets.zero,
          ),
          onChanged: (value) {
            if (value.isNotEmpty && index < 5) {
              _focusNodes[index + 1].requestFocus();
            } else if (value.isEmpty && index > 0) {
              _focusNodes[index - 1].requestFocus();
            }
            if (hasError) setState(() => _errorText = null);
          },
        ),
      ),
    );
  }
}
