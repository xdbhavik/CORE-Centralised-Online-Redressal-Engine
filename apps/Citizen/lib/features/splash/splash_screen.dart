import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/widgets/responsive_layout.dart';

/// Splash screen with CORE branding, glassmorphic logo, ambient gradients,
/// and an animated indeterminate progress bar.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen>
    with TickerProviderStateMixin {
  late final AnimationController _fadeController;
  late final Animation<double> _fadeAnimation;
  late final AnimationController _progressController;
  late final AnimationController _scanController;

  @override
  void initState() {
    super.initState();

    // Content fade-in
    _fadeController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _fadeAnimation = CurvedAnimation(
      parent: _fadeController,
      curve: Curves.easeOut,
    );

    // Progress bar
    _progressController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat();

    // Scan line effect
    _scanController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 2),
    )..repeat();

    // Start animations
    Future.delayed(const Duration(milliseconds: 100), () {
      if (mounted) _fadeController.forward();
    });

    // Navigate after delay
    Timer(const Duration(milliseconds: 2500), () {
      if (mounted) context.go('/welcome');
    });
  }

  @override
  void dispose() {
    _fadeController.dispose();
    _progressController.dispose();
    _scanController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surfaceContainerLow,
      body: ResponsiveLayout(
        mobile: _buildMobileContent(),
        desktop: _buildDesktopContent(),
      ),
    );
  }

  Widget _buildMobileContent() {
    return Stack(
      children: [
        // ── Ambient gradients ──
        Positioned.fill(
          child: CustomPaint(painter: _AmbientGradientPainter()),
        ),

        // ── Main content ──
        Center(
          child: FadeTransition(
            opacity: _fadeAnimation,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 400),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.containerMarginMobile,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Logo mark
                    _buildLogo(96),
                    const SizedBox(height: AppSpacing.space6),

                    // Wordmark
                    RichText(
                      text: TextSpan(
                        style: Theme.of(context).textTheme.displayMedium,
                        children: [
                          TextSpan(
                            text: 'CO',
                            style: TextStyle(color: AppColors.primary),
                          ),
                          TextSpan(
                            text: 'RE',
                            style: TextStyle(
                              fontWeight: FontWeight.w300,
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: AppSpacing.space2),

                    // Tagline
                    Text(
                      'Empowering resolution through\ncognitive trust.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                            color: AppColors.onSurfaceVariant
                                .withValues(alpha: 0.8),
                          ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),

        // ── Bottom loader ──
        Positioned(
          left: 0,
          right: 0,
          bottom: AppSpacing.containerMarginMobile + 24,
          child: FadeTransition(
            opacity: _fadeAnimation,
            child: Column(
              children: [
                // Indeterminate progress bar
                SizedBox(
                  width: 128,
                  height: 4,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(9999),
                    child: AnimatedBuilder(
                      animation: _progressController,
                      builder: (context, _) {
                        return CustomPaint(
                          painter: _ProgressPainter(
                            progress: _progressController.value,
                          ),
                        );
                      },
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.space3),
                Text(
                  'VERIFYING SYSTEM STATE',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: AppColors.onSurfaceVariant
                            .withValues(alpha: 0.6),
                        fontSize: 10,
                        letterSpacing: 2,
                        fontWeight: FontWeight.w500,
                      ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildDesktopContent() {
    return Stack(
      children: [
        // ── Ambient gradients ──
        Positioned.fill(
          child: CustomPaint(painter: _AmbientGradientPainter()),
        ),

        // ── Main content ──
        Center(
          child: FadeTransition(
            opacity: _fadeAnimation,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 800),
              child: Padding(
                padding: const EdgeInsets.symmetric(
                  horizontal: AppSpacing.containerMarginDesktop,
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    // Logo mark
                    _buildLogo(120),
                    const SizedBox(height: AppSpacing.space8),

                    // Wordmark
                    RichText(
                      text: TextSpan(
                        style: Theme.of(context).textTheme.displayLarge,
                        children: [
                          TextSpan(
                            text: 'CO',
                            style: TextStyle(color: AppColors.primary),
                          ),
                          TextSpan(
                            text: 'RE',
                            style: TextStyle(
                              fontWeight: FontWeight.w300,
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: AppSpacing.space3),

                    // Tagline
                    Text(
                      'Empowering resolution through\ncognitive trust.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                            color: AppColors.onSurfaceVariant
                                .withValues(alpha: 0.8),
                          ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),

        // ── Bottom loader ──
        Positioned(
          left: 0,
          right: 0,
          bottom: AppSpacing.space12 + 24,
          child: FadeTransition(
            opacity: _fadeAnimation,
            child: Column(
              children: [
                // Indeterminate progress bar
                SizedBox(
                  width: 200,
                  height: 4,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(9999),
                    child: AnimatedBuilder(
                      animation: _progressController,
                      builder: (context, _) {
                        return CustomPaint(
                          painter: _ProgressPainter(
                            progress: _progressController.value,
                          ),
                        );
                      },
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.space3),
                Text(
                  'VERIFYING SYSTEM STATE',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: AppColors.onSurfaceVariant
                            .withValues(alpha: 0.6),
                        fontSize: 10,
                        letterSpacing: 2,
                        fontWeight: FontWeight.w500,
                      ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLogo(double size) {
    return Stack(
      alignment: Alignment.center,
      children: [
        // Shadow
        Positioned(
          bottom: -8,
          child: Container(
            width: size * 0.66,
            height: 8,
            decoration: BoxDecoration(
              color: AppColors.primary.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(9999),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withValues(alpha: 0.2),
                  blurRadius: 12,
                ),
              ],
            ),
          ),
        ),
        // Logo container
        Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(16),
            boxShadow: const [
              BoxShadow(
                color: Color(0x14000000),
                blurRadius: 16,
                offset: Offset(0, 8),
              ),
              BoxShadow(
                color: Color(0x0A000000),
                blurRadius: 32,
                offset: Offset(0, 16),
              ),
            ],
          ),
          child: Stack(
            children: [
              // Glassmorphic sheen
              Positioned.fill(
                child: Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        Colors.white.withValues(alpha: 0.4),
                        Colors.transparent,
                      ],
                    ),
                  ),
                ),
              ),
              // Icon
              Center(
                child: Icon(
                  Icons.policy,
                  size: size * 0.5,
                  color: AppColors.primary,
                ),
              ),
              // Animated scan line
              AnimatedBuilder(
                animation: _scanController,
                builder: (context, _) {
                  return Positioned(
                    top: _scanController.value * size - (size * 0.5),
                    left: 0,
                    right: 0,
                    height: size * 0.5,
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topCenter,
                          end: Alignment.bottomCenter,
                          colors: [
                            Colors.transparent,
                            AppColors.primary.withValues(alpha: 0.1),
                            Colors.transparent,
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Custom painters ──

class _AmbientGradientPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    // Top-center gradient
    final topPaint = Paint()
      ..shader = RadialGradient(
        center: const Alignment(0, -1),
        radius: 1.4,
        colors: [
          AppColors.primaryContainer.withValues(alpha: 0.06),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), topPaint);

    // Bottom-right gradient
    final bottomPaint = Paint()
      ..shader = RadialGradient(
        center: const Alignment(1, 1),
        radius: 1.6,
        colors: [
          AppColors.secondaryContainer.withValues(alpha: 0.08),
          Colors.transparent,
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawRect(Rect.fromLTWH(0, 0, size.width, size.height), bottomPaint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

class _ProgressPainter extends CustomPainter {
  _ProgressPainter({required this.progress});

  final double progress;

  @override
  void paint(Canvas canvas, Size size) {
    // Background track
    final bgPaint = Paint()..color = AppColors.surfaceContainerHighest;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(0, 0, size.width, size.height),
        const Radius.circular(9999),
      ),
      bgPaint,
    );

    // Moving indicator
    final indicatorWidth = size.width * 0.33;
    final start = (size.width + indicatorWidth) * progress - indicatorWidth;
    final fgPaint = Paint()..color = AppColors.primary;
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        Rect.fromLTWH(start.clamp(0, size.width), 0,
            indicatorWidth.clamp(0, size.width - start.clamp(0, size.width)),
            size.height),
        const Radius.circular(9999),
      ),
      fgPaint,
    );
  }

  @override
  bool shouldRepaint(_ProgressPainter oldDelegate) =>
      oldDelegate.progress != progress;
}
