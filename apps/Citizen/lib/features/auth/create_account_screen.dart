import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'dart:ui' show ImageFilter;
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/core_text_field.dart';
import '../../core/widgets/staggered_entrance.dart';
import '../../core/services/auth_service.dart';
import '../../core/services/firebase_auth_service.dart';
import '../../core/config/app_config.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/theme/app_radius.dart';

class CreateAccountScreen extends StatefulWidget {
  const CreateAccountScreen({super.key});

  @override
  State<CreateAccountScreen> createState() => _CreateAccountScreenState();
}

class _CreateAccountScreenState extends State<CreateAccountScreen> {
  final _nameController = TextEditingController();
  final _mobileController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _authService = AuthService();
  final _firebaseAuth = FirebaseAuthService();
  String _selectedLanguage = 'ENGLISH';
  bool _isLoading = false;
  bool _agreeToTerms = false;
  
  // All 23 languages supported by the backend PreferredLanguage enum.
  final List<String> _languages = [
    "ENGLISH", "HINDI", "BENGALI", "TELUGU", "MARATHI", "TAMIL", "URDU",
    "GUJARATI", "KANNADA", "ODIA", "MALAYALAM", "PUNJABI", "ASSAMESE",
    "MAITHILI", "SANSKRIT", "KASHMIRI", "NEPALI", "KONKANI", "SINDHI",
    "DOGRI", "MANIPURI", "BODO", "SANTALI",
  ];

  Future<void> _handleRegister() async {
    if (!_agreeToTerms) {
      _showError('Please agree to the Terms of Service.');
      return;
    }

    final name = _nameController.text.trim();
    final mobile = _mobileController.text.trim();
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();

    if (name.isEmpty || mobile.isEmpty || password.isEmpty) return;
    if (mobile.length != 10 || int.tryParse(mobile) == null) {
      _showError('Enter a valid 10-digit mobile number.');
      return;
    }
    if (password.length < 6) {
      _showError('Password must be at least 6 characters.');
      return;
    }

    setState(() => _isLoading = true);

    if (AppConfig.useFirebaseMock) {
      // ── DEV MOCK: skip OTP entirely — backend dev-mock accepts
      // idToken = "mock-<mobile>" at /auth/firebase/register ──
      await _registerWithFirebase('mock-$mobile');
      if (mounted) setState(() => _isLoading = false);
      return;
    }

    // ── REAL FIREBASE MODE (Android: Play Integrity / Web: reCAPTCHA) ──
    final e164 = '${AppConfig.countryCode}$mobile';
    await _firebaseAuth.sendOtp(
      phoneNumber: e164,
      onCodeSent: (verificationId, resendToken) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        context.push('/verify-otp', extra: {
          'name': name,
          'mobile': mobile,
          'email': email,
          'password': password,
          'preferredLanguage': _selectedLanguage,
          'verificationId': verificationId,
        });
      },
      onAutoVerified: (credential) async {
        // Android auto-retrieval — verify without user typing the OTP
        try {
          final idToken =
              await _firebaseAuth.getIdTokenFromCredential(credential);
          await _registerWithFirebase(idToken);
        } catch (e) {
          _showError('Auto-verification failed: $e');
        } finally {
          if (mounted) setState(() => _isLoading = false);
        }
      },
      onError: (error) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        _showError(error);
      },
    );
  }

  Future<void> _registerWithFirebase(String idToken) async {
    try {
      final success = await _authService.registerWithFirebase(
        idToken: idToken,
        name: _nameController.text.trim(),
        email: _emailController.text.trim(),
        password: _passwordController.text.trim(),
        preferredLanguage: _selectedLanguage,
      );
      if (success && mounted) {
        context.go('/home');
      } else if (mounted) {
        _showError('Registration failed. Please try again.');
      }
    } on DioException catch (e) {
      // Backend ka actual reason dikhao (e.g. mobile already registered)
      if (mounted) {
        final data = e.response?.data;
        final msg = (data is Map && data['message'] != null)
            ? data['message'].toString()
            : 'Registration failed. Please try again.';
        _showError(msg);
      }
    } catch (e) {
      if (mounted) _showError('Registration failed. Please try again.');
    }
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg,
            style: AppTypography.caption.copyWith(color: AppColors.onError)),
        backgroundColor: AppColors.error,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      body: ResponsiveLayout(
        mobile: _buildMobile(context),
        desktop: _buildDesktop(context),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Stack(
      children: [
        // ── Ambient Background Blobs ──
        Positioned(
          top: -50,
          right: -50,
          child: IgnorePointer(
            child: Container(
              width: 300,
              height: 300,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
                child: const SizedBox(),
              ),
            ),
          ),
        ),
        Positioned(
          top: MediaQuery.of(context).size.height * 0.2,
          left: -100,
          child: IgnorePointer(
            child: Container(
              width: 200,
              height: 200,
              decoration: BoxDecoration(
                color: AppColors.secondaryFixed.withValues(alpha: 0.2),
                shape: BoxShape.circle,
              ),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 40, sigmaY: 40),
                child: const SizedBox(),
              ),
            ),
          ),
        ),
        
        SafeArea(
          child: Center(
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
          ),
        ),
      ],
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
                            context.go('/welcome');
                          }
                        },
                      ),
                      const SizedBox(height: AppSpacing.space6),
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: AppRadius.borderRadiusXl,
                          boxShadow: AppShadows.level2,
                        ),
                        child: const Icon(
                          Icons.diversity_2,
                          size: 48,
                          color: AppColors.primary,
                        ),
                      ),
                      const Spacer(),
                      Text(
                        'Join CORE.',
                        style: textTheme.displayLarge?.copyWith(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space3),
                      Text(
                        'Be part of the change in your community.',
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
          const SizedBox(height: AppSpacing.space4),
          // Icon Box
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: AppColors.primaryContainer,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primaryContainer.withValues(alpha: 0.15),
                  blurRadius: 30,
                  offset: const Offset(0, 8),
                )
              ],
            ),
            child: Stack(
              children: [
                Container(
                  decoration: BoxDecoration(
                    color: Colors.white.withValues(alpha: 0.2),
                    borderRadius: BorderRadius.circular(16),
                  ),
                ),
                const Center(
                  child: Icon(Icons.diversity_2, color: AppColors.onPrimaryContainer, size: 24),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.space5),
          
          // Typography
          Text('Join CORE.', style: AppTypography.displayLargeMobile.copyWith(color: AppColors.onSurface)),
          const SizedBox(height: AppSpacing.space1),
          Text('Be part of the change in your community.', style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
          if (AppConfig.useFirebaseMock) ...[
            const SizedBox(height: AppSpacing.space2),
            Text(
              'DEV MOCK mode (Firebase bypassed)',
              style: AppTypography.caption.copyWith(color: AppColors.tertiary),
            ),
          ],
          const SizedBox(height: AppSpacing.space6),
        ],
        
        // Form Fields
        CoreTextField(
                              controller: _nameController,
                              label: 'Full Name',
                              prefixIcon: Icons.person,
                              textInputAction: TextInputAction.next,
                              autofillHints: const [AutofillHints.name],
                            ),
                            const SizedBox(height: AppSpacing.space3),
                            CoreTextField(
                              controller: _mobileController,
                              label: 'Mobile Number',
                              hintText: '10-digit mobile number',
                              prefixIcon: Icons.phone,
                              keyboardType: TextInputType.phone,
                              maxLength: 10,
                              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                              autofillHints: const [AutofillHints.telephoneNumber],
                              textInputAction: TextInputAction.next,
                            ),
                            const SizedBox(height: AppSpacing.space3),
                            CoreTextField(
                              controller: _emailController,
                              label: 'Email (Optional)',
                              prefixIcon: Icons.email,
                              keyboardType: TextInputType.emailAddress,
                              autofillHints: const [AutofillHints.email],
                              textInputAction: TextInputAction.next,
                            ),
                            const SizedBox(height: AppSpacing.space3),
                            CoreTextField(
                              controller: _passwordController,
                              label: 'Password',
                              prefixIcon: Icons.lock,
                              isPassword: true,
                              autofillHints: const [AutofillHints.newPassword],
                              textInputAction: TextInputAction.done,
                              onSubmitted: (_) => _handleRegister(),
                            ),
                            const SizedBox(height: AppSpacing.space3),
                            
                            // Language Dropdown
                            Container(
                              decoration: BoxDecoration(
                                color: AppColors.surfaceContainer,
                                borderRadius: AppRadius.borderRadiusLg,
                                border: Border.all(color: AppColors.outlineVariant.withValues(alpha: 0.5)),
                              ),
                              padding: const EdgeInsets.symmetric(horizontal: 16),
                              child: DropdownButtonHideUnderline(
                                child: DropdownButton<String>(
                                  value: _selectedLanguage,
                                  isExpanded: true,
                                  icon: const Icon(Icons.language, color: AppColors.primary),
                                  dropdownColor: AppColors.surfaceContainerHighest,
                                  style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
                                  onChanged: (String? newValue) {
                                    if (newValue != null) {
                                      setState(() {
                                        _selectedLanguage = newValue;
                                      });
                                    }
                                  },
                                  items: _languages.map<DropdownMenuItem<String>>((String value) {
                                    return DropdownMenuItem<String>(
                                      value: value,
                                      child: Text(value),
                                    );
                                  }).toList(),
                                ),
                              ),
                            ),
                            const SizedBox(height: AppSpacing.space4),
                            
                            // Terms Checkbox
                            Row(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                SizedBox(
                                  width: 24,
                                  height: 24,
                                  child: Checkbox(
                                    value: _agreeToTerms,
                                    onChanged: (val) {
                                      setState(() => _agreeToTerms = val ?? false);
                                    },
                                    activeColor: AppColors.primary,
                                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                  ),
                                ),
                                const SizedBox(width: AppSpacing.space2),
                                Expanded(
                                  child: Padding(
                                    padding: const EdgeInsets.only(top: 2.0),
                                    child: RichText(
                                      text: TextSpan(
                                        style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant, height: 1.5),
                                        children: [
                                          const TextSpan(text: 'I agree to the '),
                                          TextSpan(
                                            text: 'Terms of Service',
                                            style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w500),
                                          ),
                                          const TextSpan(text: ' and acknowledge the '),
                                          TextSpan(
                                            text: 'Privacy Policy',
                                            style: TextStyle(color: AppColors.primary, fontWeight: FontWeight.w500),
                                          ),
                                          const TextSpan(text: '.'),
                                        ],
                                      ),
                                    ),
                                  ),
                                )
                              ],
                            ),
                            const SizedBox(height: AppSpacing.space6),
                            
                            // Action Button
                            CoreButton(
                              label: 'Register',
                              variant: CoreButtonVariant.primary,
                              isLoading: _isLoading,
                              icon: Icons.arrow_forward,
                              onPressed: _handleRegister,
                            ),
                            const SizedBox(height: AppSpacing.space5),
                            
                            // Footer Link
                            Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Text("Already have an account? ", style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant)),
                                GestureDetector(
                                  onTap: () {
                                    if (GoRouter.of(context).canPop()) {
                                      context.pop();
                                    } else {
                                      context.go('/login');
                                    }
                                  },
                                  child: Text('Log in', style: AppTypography.caption.copyWith(color: AppColors.primary, fontWeight: FontWeight.w500)),
                                ),
                              ],
                            ),
                            const SizedBox(height: AppSpacing.space2),
                            // Existing users who forgot their password can reset via OTP
                            Center(
                              child: GestureDetector(
                                onTap: () => context.push('/forgot-password'),
                                child: Text(
                                  'Forgot Password?',
                                  style: AppTypography.caption.copyWith(
                                    color: AppColors.primary,
                                    fontWeight: FontWeight.w500,
                                  ),
                                ),
                              ),
                            ),
        const SizedBox(height: AppSpacing.space4),
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
          Text('Register', style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface)),
          // Right profile icon placeholder
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
}
