import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/theme/app_radius.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/responsive_layout.dart';

/// Welcome / landing screen — first real screen after splash.
/// "Your city, heard." with Get Started + I have an account CTAs.
class WelcomeScreen extends StatelessWidget {
  const WelcomeScreen({super.key});

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
    final textTheme = Theme.of(context).textTheme;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
        child: Column(
          children: [
            // ── Hero area ──
            Expanded(
              flex: 5,
              child: Stack(
                children: [
                  // Background gradient + wave
                  Positioned.fill(
                    child: Container(
                      decoration: BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [
                            AppColors.surface,
                            AppColors.primaryContainer.withValues(alpha: 0.15),
                          ],
                        ),
                      ),
                    ),
                  ),
                  // Decorative wave SVG replacement
                  Positioned(
                    bottom: 0,
                    left: 0,
                    right: 0,
                    height: 120,
                    child: CustomPaint(
                      painter: _WavePainter(),
                      size: Size.infinite,
                    ),
                  ),
                  // Content
                  Positioned(
                    left: AppSpacing.containerMarginMobile,
                    right: AppSpacing.containerMarginMobile,
                    bottom: 48,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // App icon
                        Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerHighest,
                            borderRadius: AppRadius.borderRadiusXl,
                            boxShadow: AppShadows.level2,
                          ),
                          child: const Icon(
                            Icons.location_city,
                            size: 36,
                            color: AppColors.primary,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space6),

                        // Headline
                        Text(
                          'Your city,\nheard.',
                          style: textTheme.displayMedium?.copyWith(
                            color: AppColors.onSurface,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space2),

                        // Subtitle
                        SizedBox(
                          width: MediaQuery.sizeOf(context).width * 0.7,
                          child: Text(
                            'A smarter way to build a better community together.',
                            style: textTheme.bodyMedium?.copyWith(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space6),

                        // Buttons
                        CoreButton(
                          label: 'Get Started',
                          onPressed: () => context.go('/language'),
                        ),
                        const SizedBox(height: AppSpacing.space3),
                        CoreButton(
                          label: 'I have an account',
                          variant: CoreButtonVariant.secondary,
                          onPressed: () => context.go('/login'),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),

            // ── Bottom info area ──
            Container(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.containerMarginMobile,
                AppSpacing.space8,
                AppSpacing.containerMarginMobile,
                AppSpacing.space8,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Trust badge
                  Row(
                    children: [
                      Icon(
                        Icons.verified_user_outlined,
                        size: 20,
                        color: AppColors.primaryFixedDim,
                      ),
                      const SizedBox(width: AppSpacing.space4),
                      Text(
                        'Secure & Official Platform',
                        style: textTheme.bodySmall?.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.space6),

                  // Feature tiles
                  Row(
                    children: [
                      Expanded(
                        child: _FeatureTile(
                          icon: Icons.forum_outlined,
                          title: 'Report',
                          subtitle: 'Lodge issues quickly.',
                        ),
                      ),
                      const SizedBox(width: AppSpacing.space4),
                      Expanded(
                        child: _FeatureTile(
                          icon: Icons.track_changes,
                          title: 'Track',
                          subtitle: 'Real-time AI updates.',
                        ),
                      ),
                    ],
                  ),
                ],
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
            // ── Left Column (Branding & Features) ──
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
                child: Stack(
                  children: [
                    Positioned(
                      bottom: 0,
                      left: 0,
                      right: 0,
                      height: 200,
                      child: ClipRRect(
                        borderRadius: AppRadius.borderRadiusXxl,
                        child: CustomPaint(
                          painter: _WavePainter(),
                          size: Size.infinite,
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(AppSpacing.space10),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              color: AppColors.surfaceContainerHighest,
                              borderRadius: AppRadius.borderRadiusXl,
                              boxShadow: AppShadows.level2,
                            ),
                            child: const Icon(
                              Icons.location_city,
                              size: 48,
                              color: AppColors.primary,
                            ),
                          ),
                          const Spacer(),
                          Text(
                            'Your city,\nheard.',
                            style: textTheme.displayLarge?.copyWith(
                              color: AppColors.onSurface,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.space3),
                          Text(
                            'A smarter way to build a better community together.',
                            style: textTheme.bodyLarge?.copyWith(
                              color: AppColors.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.space10),
                          Row(
                            children: [
                              Expanded(
                                child: _FeatureTile(
                                  icon: Icons.forum_outlined,
                                  title: 'Report',
                                  subtitle: 'Lodge issues quickly with AI.',
                                ),
                              ),
                              const SizedBox(width: AppSpacing.space4),
                              Expanded(
                                child: _FeatureTile(
                                  icon: Icons.track_changes,
                                  title: 'Track',
                                  subtitle: 'Real-time status updates.',
                                ),
                              ),
                              const Spacer(),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),

            // ── Right Column (Actions) ──
            Expanded(
              flex: 9, // 45%
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space10),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Welcome to CORE',
                      style: textTheme.headlineMedium,
                    ),
                    const SizedBox(height: AppSpacing.space2),
                    Text(
                      'Get started by creating a new account or logging in to your existing one.',
                      style: textTheme.bodyMedium?.copyWith(
                        color: AppColors.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: AppSpacing.space8),
                    CoreButton(
                      label: 'Get Started',
                      onPressed: () => context.go('/language'),
                    ),
                    const SizedBox(height: AppSpacing.space3),
                    CoreButton(
                      label: 'I have an account',
                      variant: CoreButtonVariant.secondary,
                      onPressed: () => context.go('/login'),
                    ),
                    const SizedBox(height: AppSpacing.space8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.verified_user_outlined,
                          size: 20,
                          color: AppColors.primaryFixedDim,
                        ),
                        const SizedBox(width: AppSpacing.space4),
                        Text(
                          'Secure & Official Platform',
                          style: textTheme.bodySmall?.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Feature tile matching the Stitch welcome design.
class _FeatureTile extends StatelessWidget {
  const _FeatureTile({
    required this.icon,
    required this.title,
    required this.subtitle,
  });

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Container(
      padding: const EdgeInsets.all(AppSpacing.space4),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: AppRadius.borderRadiusXl,
        boxShadow: AppShadows.level1,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppColors.secondary, size: 24),
          const SizedBox(height: AppSpacing.space2),
          Text(
            title,
            style: textTheme.titleLarge?.copyWith(
              fontSize: 16,
              height: 24 / 16,
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space1),
          Text(
            subtitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: textTheme.bodySmall?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

/// Decorative wave painter for the hero background.
class _WavePainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = AppColors.primary.withValues(alpha: 0.06)
      ..style = PaintingStyle.fill;

    final path = Path()
      ..moveTo(0, size.height)
      ..lineTo(size.width, size.height)
      ..lineTo(size.width, size.height * 0.2)
      ..quadraticBezierTo(
        size.width * 0.75,
        size.height * 0.0,
        size.width * 0.5,
        size.height * 0.2,
      )
      ..quadraticBezierTo(
        size.width * 0.25,
        size.height * 0.4,
        0,
        size.height * 0.2,
      )
      ..close();

    canvas.drawPath(path, paint);

    // Second wave (lighter)
    final paint2 = Paint()
      ..color = AppColors.primary.withValues(alpha: 0.03)
      ..style = PaintingStyle.fill;

    final path2 = Path()
      ..moveTo(0, size.height)
      ..lineTo(size.width, size.height)
      ..lineTo(size.width, size.height * 0.5)
      ..quadraticBezierTo(
        size.width * 0.75,
        size.height * 0.3,
        size.width * 0.5,
        size.height * 0.5,
      )
      ..quadraticBezierTo(
        size.width * 0.25,
        size.height * 0.7,
        0,
        size.height * 0.5,
      )
      ..close();

    canvas.drawPath(path2, paint2);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
