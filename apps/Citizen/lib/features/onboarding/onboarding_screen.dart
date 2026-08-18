import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/theme/app_radius.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/responsive_layout.dart';

/// 5-page onboarding flow presented as a swipeable PageView.
///
/// Pages:
/// 1. Voice & Image — "Speak or Snap"
/// 2. AI Assistant — "Meet your AI Assistant"
/// 3. Live Tracking — "Transparency in real-time"
/// 4. Privacy & Offline — "Secure & Reliable"
/// 5. Accessibility — "Designed for everyone"
class OnboardingScreen extends StatefulWidget {
  const OnboardingScreen({super.key});

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  static const int _totalPages = 5;

  void _nextPage() {
    if (_currentPage < _totalPages - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 350),
        curve: Curves.easeInOut,
      );
    } else {
      _finishOnboarding();
    }
  }

  void _finishOnboarding() {
    context.go('/login');
  }

  void _skip() {
    _finishOnboarding();
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
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
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
        child: SafeArea(
          child: Column(
            children: [
              // ── Top bar: page indicator + skip ──
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.containerMarginMobile,
                  AppSpacing.space4,
                  AppSpacing.containerMarginMobile,
                  0,
                ),
                child: Row(
                  children: [
                    // Page dots
                    Row(
                      children: List.generate(
                        _totalPages,
                        (i) => _PageDot(isActive: i == _currentPage),
                      ),
                    ),
                    const Spacer(),
                    // Page counter
                    Text(
                      '${_currentPage + 1} / $_totalPages',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                    ),
                  ],
                ),
              ),

              // ── Pages ──
              Expanded(
                child: PageView(
                  controller: _pageController,
                  onPageChanged: (i) => setState(() => _currentPage = i),
                  children: const [
                    _VoiceImagePage(),
                    _AIAssistantPage(),
                    _LiveTrackingPage(),
                    _PrivacyOfflinePage(),
                    _AccessibilityPage(),
                  ],
                ),
              ),

              // ── Bottom CTA ──
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  AppSpacing.containerMarginMobile,
                  AppSpacing.space4,
                  AppSpacing.containerMarginMobile,
                  AppSpacing.space8,
                ),
                child: Column(
                  children: [
                    CoreButton(
                      label: _currentPage == _totalPages - 1
                          ? 'Get Started'
                          : _currentPage == 0
                              ? 'Continue'
                              : 'Next',
                      icon: Icons.arrow_forward,
                      onPressed: _nextPage,
                    ),
                    if (_currentPage < _totalPages - 1) ...[
                      const SizedBox(height: AppSpacing.space3),
                      CoreButton(
                        label: 'SKIP FOR NOW',
                        variant: CoreButtonVariant.text,
                        isFullWidth: false,
                        onPressed: _skip,
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1200),
        child: Row(
          children: [
            // ── Left Column (Content) ──
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
                    // Pages
                    PageView(
                      controller: _pageController,
                      onPageChanged: (i) => setState(() => _currentPage = i),
                      children: const [
                        _VoiceImagePage(),
                        _AIAssistantPage(),
                        _LiveTrackingPage(),
                        _PrivacyOfflinePage(),
                        _AccessibilityPage(),
                      ],
                    ),
                    
                    // Top bar overlay
                    Positioned(
                      top: AppSpacing.space6,
                      left: AppSpacing.space8,
                      right: AppSpacing.space8,
                      child: Row(
                        children: [
                          Row(
                            children: List.generate(
                              _totalPages,
                              (i) => _PageDot(isActive: i == _currentPage),
                            ),
                          ),
                          const Spacer(),
                          Text(
                            '${_currentPage + 1} / $_totalPages',
                            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                  color: AppColors.onSurfaceVariant,
                                ),
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
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
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
                            Icons.explore,
                            size: 48,
                            color: AppColors.primary,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space6),
                        Text(
                          'Welcome to CORE.',
                          style: Theme.of(context).textTheme.displayLarge?.copyWith(
                                color: AppColors.onSurface,
                              ),
                        ),
                        const SizedBox(height: AppSpacing.space3),
                        Text(
                          'Discover a new way to interact with your community and local authorities.',
                          style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                color: AppColors.onSurfaceVariant,
                              ),
                        ),
                        const SizedBox(height: AppSpacing.space10),
                        CoreButton(
                          label: _currentPage == _totalPages - 1
                              ? 'Get Started'
                              : _currentPage == 0
                                  ? 'Continue'
                                  : 'Next Step',
                          icon: Icons.arrow_forward,
                          onPressed: _nextPage,
                        ),
                        if (_currentPage < _totalPages - 1) ...[
                          const SizedBox(height: AppSpacing.space4),
                          CoreButton(
                            label: 'Skip Onboarding',
                            variant: CoreButtonVariant.secondary,
                            icon: Icons.skip_next,
                            onPressed: _skip,
                          ),
                        ],
                      ],
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
}

// ═══════════════════════════════════════════════════════════════════
// Page Dot Indicator
// ═══════════════════════════════════════════════════════════════════

class _PageDot extends StatelessWidget {
  const _PageDot({required this.isActive});
  final bool isActive;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 250),
      margin: const EdgeInsets.only(right: 6),
      width: isActive ? 24 : 8,
      height: 8,
      decoration: BoxDecoration(
        color: isActive ? AppColors.primary : AppColors.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(9999),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Page 1 — Voice & Image ("Speak or Snap")
// ═══════════════════════════════════════════════════════════════════

class _VoiceImagePage extends StatelessWidget {
  const _VoiceImagePage();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
      ),
      child: Column(
        children: [
          const Spacer(flex: 1),

          // ── Illustration card ──
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(AppSpacing.space4),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: AppRadius.borderRadiusXl,
              border: Border.all(
                color: AppColors.outlineVariant.withValues(alpha: 0.3),
              ),
            ),
            child: Column(
              children: [
                // AI Vision badge
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceContainerLowest,
                        borderRadius: AppRadius.borderRadiusFull,
                        boxShadow: AppShadows.level1,
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            Icons.visibility,
                            size: 14,
                            color: AppColors.primary,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            'AI VISION',
                            style: textTheme.bodySmall?.copyWith(
                              fontWeight: FontWeight.w600,
                              fontSize: 10,
                              letterSpacing: 1,
                              color: AppColors.onSurface,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.space4),

                // Simulated camera view with detection
                Container(
                  height: 140,
                  width: double.infinity,
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                      colors: [
                        AppColors.surfaceContainerHigh,
                        AppColors.surfaceContainer,
                      ],
                    ),
                    borderRadius: AppRadius.borderRadiusMd,
                  ),
                  child: Stack(
                    children: [
                      // Detection box
                      Positioned(
                        top: 20,
                        left: 40,
                        child: Container(
                          width: 120,
                          height: 80,
                          decoration: BoxDecoration(
                            border: Border.all(
                              color: AppColors.primary,
                              width: 2,
                            ),
                            borderRadius: AppRadius.borderRadiusSm,
                          ),
                        ),
                      ),
                      // Detection label
                      Positioned(
                        top: 16,
                        left: 56,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.primary,
                            borderRadius: AppRadius.borderRadiusSm,
                          ),
                          child: Text(
                            'Pothole Detected',
                            style: textTheme.bodySmall?.copyWith(
                              color: AppColors.onPrimary,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.space4),

                // Voice input bar
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space4,
                    vertical: AppSpacing.space3,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerLowest,
                    borderRadius: AppRadius.borderRadiusFull,
                    boxShadow: AppShadows.level1,
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 32,
                        height: 32,
                        decoration: BoxDecoration(
                          color: AppColors.primary,
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(
                          Icons.mic,
                          size: 16,
                          color: AppColors.onPrimary,
                        ),
                      ),
                      const SizedBox(width: AppSpacing.space3),
                      // Waveform
                      ...List.generate(
                        7,
                        (i) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 2),
                          child: Container(
                            width: 3,
                            height: [12, 20, 16, 24, 14, 22, 10][i].toDouble(),
                            decoration: BoxDecoration(
                              color: AppColors.primary,
                              borderRadius: AppRadius.borderRadiusFull,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.space3),
                      Expanded(
                        child: Text(
                          '"There\'s a massive pothole on...',
                          style: textTheme.bodySmall?.copyWith(
                            color: AppColors.onSurfaceVariant,
                            fontStyle: FontStyle.italic,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.space3),

                // Tags
                Row(
                  children: [
                    _TagChip(label: 'Location: 5th Ave'),
                    const SizedBox(width: 8),
                    _TagChip(label: 'Type: Pothole'),
                  ],
                ),
              ],
            ),
          ),

          const Spacer(flex: 1),

          // ── Text content ──
          Text(
            'Speak or Snap',
            style: textTheme.headlineMedium?.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            "Don't want to type? Record a voice message or upload a photo. Our AI identifies locations and details automatically.",
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),

          const Spacer(flex: 1),
        ],
      ),
    );
  }
}

class _TagChip extends StatelessWidget {
  const _TagChip({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(
        color: AppColors.primaryFixed.withValues(alpha: 0.4),
        borderRadius: AppRadius.borderRadiusFull,
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.bodySmall?.copyWith(
              color: AppColors.primary,
              fontWeight: FontWeight.w500,
              fontSize: 11,
            ),
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Page 2 — AI Assistant ("Meet your AI Assistant")
// ═══════════════════════════════════════════════════════════════════

class _AIAssistantPage extends StatelessWidget {
  const _AIAssistantPage();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
      ),
      child: Column(
        children: [
          const Spacer(flex: 1),

          // ── Reasoning card ──
          Stack(
            children: [
              // Ambient Glow
              Positioned(
                left: -16,
                top: -16,
                right: -16,
                bottom: -16,
                child: Container(
                  decoration: BoxDecoration(
                    color: AppColors.primary.withValues(alpha: 0.2),
                    shape: BoxShape.circle,
                  ),
                ),
              ),
              // Glassmorphic Card
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(AppSpacing.space6),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainerLowest.withValues(alpha: 0.6),
                  borderRadius: AppRadius.borderRadiusXl,
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.4),
                    width: 1,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.08),
                      blurRadius: 32,
                      offset: const Offset(0, 8),
                    )
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // AI Reasoning badge
                    Row(
                      children: [
                        Container(
                          width: 32,
                          height: 32,
                          decoration: const BoxDecoration(
                            color: AppColors.primaryContainer,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.psychology,
                            size: 18,
                            color: AppColors.onPrimaryContainer,
                          ),
                        ),
                        const SizedBox(width: AppSpacing.space3),
                        Text(
                          'AI REASONING',
                          style: textTheme.labelSmall?.copyWith(
                            color: AppColors.secondary,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.2,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.space4),

                    // Quote
                    Container(
                      padding: const EdgeInsets.all(AppSpacing.space3),
                      decoration: BoxDecoration(
                        color: AppColors.surfaceVariant.withValues(alpha: 0.5),
                        borderRadius: AppRadius.borderRadiusLg,
                      ),
                      child: Text(
                        '"The streetlight outside my house on Elm St. has been flickering for a week..."',
                        style: textTheme.bodyMedium?.copyWith(
                          color: AppColors.onSurfaceVariant,
                          fontStyle: FontStyle.italic,
                        ),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.space4),

                    // Analysis steps with connector line
                    IntrinsicHeight(
                      child: Stack(
                        children: [
                          // Connector Line
                          Positioned(
                            left: 11,
                            top: 16,
                            bottom: 16,
                            child: Container(
                              width: 1,
                              color: AppColors.outlineVariant.withValues(alpha: 0.4),
                            ),
                          ),
                          Column(
                            children: [
                              // Extracted entity
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    width: 24,
                                    height: 24,
                                    margin: const EdgeInsets.only(top: 2),
                                    decoration: const BoxDecoration(
                                      color: AppColors.primary,
                                      shape: BoxShape.circle,
                                    ),
                                    child: const Icon(
                                      Icons.check,
                                      size: 14,
                                      color: AppColors.onPrimary,
                                    ),
                                  ),
                                  const SizedBox(width: AppSpacing.space3),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          'Extracted Entity',
                                          style: textTheme.bodyMedium?.copyWith(
                                            color: AppColors.onSurface,
                                          ),
                                        ),
                                        Text(
                                          'Location: Elm St.',
                                          style: textTheme.bodySmall?.copyWith(
                                            color: AppColors.secondary,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: AppSpacing.space4),

                              // Categorization
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Container(
                                    width: 24,
                                    height: 24,
                                    margin: const EdgeInsets.only(top: 2),
                                    decoration: const BoxDecoration(
                                      color: AppColors.primaryContainer,
                                      shape: BoxShape.circle,
                                    ),
                                    child: const Icon(
                                      Icons.bolt,
                                      size: 14,
                                      color: AppColors.onPrimaryContainer,
                                    ),
                                  ),
                                  const SizedBox(width: AppSpacing.space3),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Row(
                                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                          children: [
                                            Text(
                                              'Categorization',
                                              style: textTheme.bodyMedium?.copyWith(
                                                color: AppColors.onSurface,
                                              ),
                                            ),
                                            Container(
                                              padding: const EdgeInsets.symmetric(
                                                horizontal: 8,
                                                vertical: 2,
                                              ),
                                              decoration: BoxDecoration(
                                                color: AppColors.primary.withValues(alpha: 0.1),
                                                borderRadius: AppRadius.borderRadiusFull,
                                              ),
                                              child: Text(
                                                '98% Match',
                                                style: textTheme.labelSmall?.copyWith(
                                                  color: AppColors.primary,
                                                ),
                                              ),
                                            ),
                                          ],
                                        ),
                                        Text(
                                          'Public Works > Lighting',
                                          style: textTheme.bodySmall?.copyWith(
                                            color: AppColors.secondary,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),

          const Spacer(flex: 1),

          // ── Text content ──
          Text(
            'Meet your AI Assistant.',
            style: textTheme.headlineMedium?.copyWith(
              color: AppColors.onSurface,
              fontSize: 24,
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Describe your issue in your own words. Our AI understands context, categorizes accurately, and routes to the right department instantly.',
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),

          const Spacer(flex: 1),
        ],
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Page 3 — Live Tracking ("Transparency in real-time")
// ═══════════════════════════════════════════════════════════════════

class _LiveTrackingPage extends StatelessWidget {
  const _LiveTrackingPage();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
      ),
      child: Column(
        children: [
          const Spacer(flex: 1),

          // ── Timeline illustration ──
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(AppSpacing.space6),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: AppRadius.borderRadiusXl,
              border: Border.all(
                color: AppColors.outlineVariant.withValues(alpha: 0.3),
              ),
            ),
            child: Column(
              children: [
                // Step 1: Complaint Lodged (completed)
                _TimelineStep(
                  icon: Icons.check_circle,
                  iconColor: AppColors.primary,
                  title: 'Complaint Lodged',
                  subtitle: 'Today, 09:41 AM',
                  isActive: true,
                  showLine: true,
                ),
                const SizedBox(height: AppSpacing.space2),

                // Step 2: Assigned to Officer (in progress)
                _TimelineStep(
                  icon: Icons.circle,
                  iconColor: AppColors.primary,
                  title: 'Assigned to Officer',
                  subtitle: 'AI Processing • In Progress',
                  isActive: true,
                  showLine: true,
                  child: Container(
                    margin: const EdgeInsets.only(top: 8),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      borderRadius: AppRadius.borderRadiusMd,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        CircleAvatar(
                          radius: 10,
                          backgroundColor: AppColors.error,
                          child: const Icon(
                            Icons.person,
                            size: 12,
                            color: AppColors.onError,
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(
                          'S. Kumar, Ward 4',
                          style: textTheme.bodySmall?.copyWith(
                            color: AppColors.onSurface,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.space2),

                // Step 3: Resolution (pending)
                _TimelineStep(
                  icon: Icons.circle_outlined,
                  iconColor: AppColors.outlineVariant,
                  title: 'Resolution',
                  subtitle: 'Pending',
                  isActive: false,
                  showLine: false,
                ),
              ],
            ),
          ),

          const Spacer(flex: 1),

          // ── Text content ──
          RichText(
            textAlign: TextAlign.center,
            text: TextSpan(
              style: textTheme.headlineMedium?.copyWith(
                color: AppColors.onSurface,
                fontSize: 24,
              ),
              children: [
                const TextSpan(text: 'Transparency in\n'),
                TextSpan(
                  text: 'real-time.',
                  style: TextStyle(
                    fontStyle: FontStyle.italic,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Follow every step of your grievance resolution. Get notified of progress, officer assignments, and final actions instantly.',
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),

          const Spacer(flex: 1),
        ],
      ),
    );
  }
}

class _TimelineStep extends StatelessWidget {
  const _TimelineStep({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    required this.isActive,
    required this.showLine,
    this.child,
  });

  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final bool isActive;
  final bool showLine;
  final Widget? child;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Icon + line
          SizedBox(
            width: 32,
            child: Column(
              children: [
                Icon(icon, size: 20, color: iconColor),
                if (showLine)
                  Expanded(
                    child: Container(
                      width: 2,
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      color: isActive
                          ? AppColors.primary.withValues(alpha: 0.3)
                          : AppColors.outlineVariant.withValues(alpha: 0.3),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(width: AppSpacing.space3),

          // Content
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: textTheme.titleLarge?.copyWith(
                    fontSize: 14,
                    color: isActive
                        ? AppColors.onSurface
                        : AppColors.onSurfaceVariant,
                  ),
                ),
                Text(
                  subtitle,
                  style: textTheme.bodySmall?.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
                ?child,
                const SizedBox(height: AppSpacing.space2),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Page 4 — Privacy & Offline ("Secure & Reliable")
// ═══════════════════════════════════════════════════════════════════

class _PrivacyOfflinePage extends StatelessWidget {
  const _PrivacyOfflinePage();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
      ),
      child: Column(
        children: [
          const Spacer(flex: 2),

          // ── Shield illustration ──
          Container(
            width: 120,
            height: 120,
            decoration: BoxDecoration(
              color: AppColors.primaryFixed.withValues(alpha: 0.3),
              borderRadius: BorderRadius.circular(28),
            ),
            child: Stack(
              alignment: Alignment.center,
              children: [
                Icon(
                  Icons.shield,
                  size: 56,
                  color: AppColors.primary,
                ),
                Positioned(
                  right: 16,
                  bottom: 16,
                  child: Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainerLowest,
                      shape: BoxShape.circle,
                      boxShadow: AppShadows.level1,
                    ),
                    child: Icon(
                      Icons.link_off,
                      size: 18,
                      color: AppColors.primary,
                    ),
                  ),
                ),
              ],
            ),
          ),

          const Spacer(flex: 1),

          // ── Text content ──
          Text(
            'Secure &\nReliable',
            textAlign: TextAlign.center,
            style: textTheme.headlineMedium?.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space4),
          Text(
            "Your data is protected by enterprise-grade encryption. Lodge complaints even without internet — we'll sync them automatically when you're back online.",
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),

          const Spacer(flex: 2),
        ],
      ),
    );
  }
}

// ═══════════════════════════════════════════════════════════════════
// Page 5 — Accessibility ("Designed for everyone")
// ═══════════════════════════════════════════════════════════════════

class _AccessibilityPage extends StatelessWidget {
  const _AccessibilityPage();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;

    return Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
      ),
      child: Column(
        children: [
          const Spacer(flex: 2),

          // ── 2×2 feature grid ──
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: AppSpacing.space4,
            crossAxisSpacing: AppSpacing.space4,
            childAspectRatio: 1.1,
            children: const [
              _AccessibilityTile(
                icon: Icons.hearing,
                label: 'Screen\nReader',
                color: AppColors.primary,
              ),
              _AccessibilityTile(
                icon: Icons.contrast,
                label: 'High\nContrast',
                color: AppColors.onSurface,
              ),
              _AccessibilityTile(
                icon: Icons.record_voice_over,
                label: 'Voice\nNav',
                color: AppColors.tertiary,
              ),
              _AccessibilityTile(
                icon: Icons.translate,
                label: 'Languages',
                color: AppColors.primary,
              ),
            ],
          ),

          const Spacer(flex: 1),

          // ── Text content ──
          RichText(
            textAlign: TextAlign.center,
            text: TextSpan(
              style: textTheme.headlineMedium?.copyWith(
                color: AppColors.onSurface,
                fontSize: 24,
              ),
              children: [
                const TextSpan(text: 'Designed for\n'),
                TextSpan(
                  text: 'everyone.',
                  style: TextStyle(
                    fontStyle: FontStyle.italic,
                    decoration: TextDecoration.underline,
                    decorationColor: AppColors.primary,
                    color: AppColors.primary,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Full support for assistive technologies, voice navigation, and multiple languages. CORE belongs to every citizen.',
            textAlign: TextAlign.center,
            style: textTheme.bodyMedium?.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),

          const Spacer(flex: 2),
        ],
      ),
    );
  }
}

class _AccessibilityTile extends StatelessWidget {
  const _AccessibilityTile({
    required this.icon,
    required this.label,
    required this.color,
  });

  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: AppRadius.borderRadiusXl,
        border: Border.all(
          color: AppColors.outlineVariant.withValues(alpha: 0.2),
        ),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 28, color: color),
          const SizedBox(height: AppSpacing.space2),
          Text(
            label,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleLarge?.copyWith(
                  fontSize: 14,
                  color: AppColors.onSurface,
                ),
          ),
        ],
      ),
    );
  }
}
