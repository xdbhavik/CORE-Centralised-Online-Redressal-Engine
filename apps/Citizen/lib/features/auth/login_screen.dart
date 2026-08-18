import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/core_text_field.dart';
import '../../core/widgets/staggered_entrance.dart';
import '../../core/services/auth_service.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/theme/app_radius.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _mobileController = TextEditingController();
  final _passwordController = TextEditingController();
  final _authService = AuthService();
  bool _isLoading = false;
  String? _errorText;

  @override
  void dispose() {
    _mobileController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /// Backend ka actual error message nikalta hai (invalid credentials etc.).
  String _backendError(Object e) {
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map && data['message'] != null) {
        return data['message'].toString();
      }
      if (e.response?.statusCode == 401) {
        return 'Invalid mobile number or password.';
      }
    }
    return 'Could not sign in. Check your connection and try again.';
  }

  Future<void> _handleLogin() async {
    final mobile = _mobileController.text.trim();
    final password = _passwordController.text;

    if (mobile.length != 10 || int.tryParse(mobile) == null) {
      setState(() => _errorText = 'Enter a valid 10-digit mobile number.');
      return;
    }
    if (password.isEmpty) {
      setState(() => _errorText = 'Enter your password.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorText = null;
    });

    try {
      final success = await _authService.login(mobile, password);
      if (!mounted) return;
      setState(() => _isLoading = false);
      if (success) {
        context.go('/home');
      } else {
        setState(
            () => _errorText = 'Invalid mobile number or password.');
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isLoading = false;
        _errorText = _backendError(e);
      });
    }
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
        // Ambient background effects
        Positioned(
          top: -100,
          left: -100,
          child: Container(
            width: 400,
            height: 400,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.05),
              shape: BoxShape.circle,
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
                      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space3, vertical: AppSpacing.space4),
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
                          gradient: const LinearGradient(
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                            colors: [AppColors.primary, AppColors.primaryContainer],
                          ),
                          borderRadius: AppRadius.borderRadiusXl,
                          boxShadow: AppShadows.level2,
                        ),
                        child: const Icon(Icons.shield, size: 48, color: Colors.white),
                      ),
                      const SizedBox(height: AppSpacing.space3),
                      Text(
                        'CORE',
                        style: Theme.of(context).textTheme.displayLarge?.copyWith(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 3,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Centralised Online Redressal Engine',
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
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
          // CORE Logo
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [AppColors.primary, AppColors.primaryContainer],
              ),
              borderRadius: BorderRadius.circular(20),
              boxShadow: AppShadows.level2,
            ),
            child: const Icon(Icons.shield, color: Colors.white, size: 36),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'CORE',
            style: AppTypography.displayLargeMobile.copyWith(
              color: AppColors.primary,
              fontWeight: FontWeight.w800,
              letterSpacing: 2,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            'Centralised Online Redressal Engine',
            style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.space5),
        ],
        
        // Inputs — autofill group enables OS-level saved credentials.
        AutofillGroup(
          child: Column(
            children: [
              CoreTextField(
                controller: _mobileController,
                label: 'Mobile Number',
                hintText: '10-digit mobile number',
                prefixIcon: Icons.phone_android,
                keyboardType: TextInputType.phone,
                maxLength: 10,
                inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                autofillHints: const [AutofillHints.telephoneNumber],
                textInputAction: TextInputAction.next,
                onChanged: (_) {
                  if (_errorText != null) setState(() => _errorText = null);
                },
              ),
              const SizedBox(height: AppSpacing.space3),
              CoreTextField(
                controller: _passwordController,
                label: 'Password',
                isPassword: true,
                prefixIcon: Icons.lock_outline,
                autofillHints: const [AutofillHints.password],
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _handleLogin(),
                onChanged: (_) {
                  if (_errorText != null) setState(() => _errorText = null);
                },
              ),
            ],
          ),
        ),

        // Inline error banner
        if (_errorText != null) ...[
          const SizedBox(height: AppSpacing.space3),
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
        ],

        const SizedBox(height: AppSpacing.space2),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: () {
              context.push('/forgot-password');
            },
            child: Text('Forgot Password?', style: AppTypography.labelSmall.copyWith(color: AppColors.primary)),
          ),
        ),

        const SizedBox(height: AppSpacing.space4),

        CoreButton(
          label: 'Sign In',
          variant: CoreButtonVariant.primary,
          isLoading: _isLoading,
          icon: Icons.arrow_forward,
          onPressed: _handleLogin,
        ),

        const SizedBox(height: AppSpacing.space3),

        // Passwordless login via Firebase OTP (POST /auth/firebase/login)
        CoreButton(
          label: 'Login with OTP',
          variant: CoreButtonVariant.secondary,
          icon: Icons.sms_outlined,
          onPressed: _isLoading
              ? null
              : () => context.push('/otp-login'),
        ),

        const SizedBox(height: AppSpacing.space5),

        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text("Don't have an account?", style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
            TextButton(
              onPressed: () {
                context.push('/register');
              },
              child: Text('Register', style: AppTypography.labelSmall.copyWith(color: AppColors.primary)),
            ),
          ],
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
                context.go('/welcome');
              }
            },
          ),
          Text('Login', style: AppTypography.titleLarge),
          const SizedBox(width: 48), // Spacer for balance
        ],
      ),
    );
  }


}
