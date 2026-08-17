import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../theme/app_animations.dart';
import '../theme/app_shadows.dart';

class CitizenBottomNavbar extends StatefulWidget {
  final Widget child;
  const CitizenBottomNavbar({super.key, required this.child});

  @override
  State<CitizenBottomNavbar> createState() => _CitizenBottomNavbarState();
}

class _CitizenBottomNavbarState extends State<CitizenBottomNavbar> {
  int _calculateSelectedIndex(BuildContext context) {
    final String location = GoRouterState.of(context).uri.path;
    if (location.startsWith('/home')) {
      return 0;
    }
    if (location.startsWith('/my-complaints')) {
      return 1;
    }
    if (location.startsWith('/alerts')) {
      return 2;
    }
    if (location.startsWith('/profile')) {
      return 3;
    }
    return 0;
  }

  void _onItemTapped(int index, BuildContext context) {
    final currentIndex = _calculateSelectedIndex(context);
    if (index == currentIndex) return;
    HapticFeedback.selectionClick();
    switch (index) {
      case 0:
        GoRouter.of(context).go('/home');
        break;
      case 1:
        GoRouter.of(context).go('/my-complaints');
        break;
      case 2:
        GoRouter.of(context).go('/alerts');
        break;
      case 3:
        GoRouter.of(context).go('/profile');
        break;
    }
  }

  @override
  Widget build(BuildContext context) {
    // Hide bottom nav on desktop, as we use DesktopSidebar there.
    final bool isDesktop = MediaQuery.of(context).size.width >= 900;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: widget.child,
      bottomNavigationBar: isDesktop
          ? null
          : _buildFloatingNav(context, _calculateSelectedIndex(context)),
    );
  }

  /// Floating pill-style navigation bar with an animated active indicator.
  Widget _buildFloatingNav(BuildContext context, int currentIndex) {
    const navItems = [
      (icon: Icons.home_outlined, activeIcon: Icons.home, label: 'Home'),
      (
        icon: Icons.assignment_outlined,
        activeIcon: Icons.assignment,
        label: 'Complaints'
      ),
      (
        icon: Icons.notifications_outlined,
        activeIcon: Icons.notifications,
        label: 'Alerts'
      ),
      (icon: Icons.person_outline, activeIcon: Icons.person, label: 'Profile'),
    ];

    return SafeArea(
      child: Container(
        margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLowest.withValues(alpha: 0.96),
          borderRadius: BorderRadius.circular(28),
          boxShadow: AppShadows.level2,
          border: Border.all(
            color: AppColors.outlineVariant.withValues(alpha: 0.25),
          ),
        ),
        child: Row(
          children: List.generate(navItems.length, (i) {
            final item = navItems[i];
            final isActive = currentIndex == i;
            return Expanded(
              child: GestureDetector(
                onTap: () => _onItemTapped(i, context),
                behavior: HitTestBehavior.opaque,
                child: AnimatedContainer(
                  duration: AppAnimations.modalOpen,
                  curve: AppAnimations.modalOpenCurve,
                  padding:
                      const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                  decoration: BoxDecoration(
                    color: isActive
                        ? AppColors.primaryContainer.withValues(alpha: 0.18)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // Icon with smooth crossfade between outlined/filled
                      AnimatedSwitcher(
                        duration: AppAnimations.modalOpen,
                        switchInCurve: AppAnimations.modalOpenCurve,
                        switchOutCurve: AppAnimations.modalCloseCurve,
                        transitionBuilder: (child, animation) =>
                            ScaleTransition(scale: animation, child: child),
                        child: Icon(
                          isActive ? item.activeIcon : item.icon,
                          key: ValueKey('${item.label}_$isActive'),
                          size: 24,
                          color: isActive
                              ? AppColors.primary
                              : AppColors.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 3),
                      AnimatedDefaultTextStyle(
                        duration: AppAnimations.buttonPress,
                        style: AppTypography.labelSmall.copyWith(
                          fontSize: 11,
                          color: isActive
                              ? AppColors.primary
                              : AppColors.onSurfaceVariant,
                          fontWeight:
                              isActive ? FontWeight.w600 : FontWeight.w400,
                        ),
                        child: Text(item.label),
                      ),
                      const SizedBox(height: 3),
                      // Active dot indicator
                      AnimatedContainer(
                        duration: AppAnimations.modalOpen,
                        curve: AppAnimations.modalOpenCurve,
                        width: isActive ? 16 : 0,
                        height: 3,
                        decoration: BoxDecoration(
                          color: isActive
                              ? AppColors.primary
                              : Colors.transparent,
                          borderRadius: BorderRadius.circular(2),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ),
      ),
    );
  }
}
