import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../theme/app_animations.dart';
import '../services/auth_service.dart';

/// Desktop sidebar navigation for post-auth citizen screens.
/// Replaces the mobile bottom navigation bar on wide screens.
class DesktopSidebar extends StatefulWidget {
  /// The main content area displayed beside the sidebar.
  final Widget child;

  /// Currently active route path (e.g. '/home', '/my-complaints').
  final String currentRoute;

  const DesktopSidebar({
    super.key,
    required this.child,
    required this.currentRoute,
  });

  @override
  State<DesktopSidebar> createState() => _DesktopSidebarState();
}

class _DesktopSidebarState extends State<DesktopSidebar> {
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        // ── Sidebar ──
        Container(
          width: 260,
          decoration: BoxDecoration(
            color: AppColors.surface,
            border: Border(
              right: BorderSide(
                color: AppColors.outlineVariant.withValues(alpha: 0.3),
                width: 1,
              ),
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 16,
                offset: const Offset(2, 0),
              ),
            ],
          ),
          child: SafeArea(
            child: Column(
              children: [
                // ── Logo ──
                Padding(
                  padding: const EdgeInsets.fromLTRB(24, 24, 24, 8),
                  child: Row(
                    children: [
                      Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: AppColors.primaryContainer,
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(
                          Icons.account_balance,
                          color: AppColors.onPrimaryContainer,
                          size: 22,
                        ),
                      ),
                      const SizedBox(width: 12),
                      RichText(
                        text: TextSpan(
                          children: [
                            TextSpan(
                              text: 'CO',
                              style: AppTypography.titleLarge.copyWith(
                                color: AppColors.primary,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            TextSpan(
                              text: 'RE',
                              style: AppTypography.titleLarge.copyWith(
                                color: AppColors.onSurfaceVariant,
                                fontWeight: FontWeight.w300,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),

                const SizedBox(height: 8),
                Divider(
                  color: AppColors.outlineVariant.withValues(alpha: 0.3),
                  height: 1,
                  indent: 24,
                  endIndent: 24,
                ),
                const SizedBox(height: 8),

                // ── Nav items ──
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    child: Column(
                      children: [
                        _NavItem(
                          icon: Icons.dashboard_outlined,
                          activeIcon: Icons.dashboard,
                          label: 'Dashboard',
                          isActive: widget.currentRoute == '/home',
                          onTap: () => context.go('/home'),
                        ),
                        const SizedBox(height: 4),
                        _NavItem(
                          icon: Icons.assignment_outlined,
                          activeIcon: Icons.assignment,
                          label: 'My Complaints',
                          isActive: widget.currentRoute == '/my-complaints',
                          onTap: () => context.go('/my-complaints'),
                        ),
                        const SizedBox(height: 4),
                        _NavItem(
                          icon: Icons.notifications_outlined,
                          activeIcon: Icons.notifications,
                          label: 'Alerts',
                          isActive: widget.currentRoute == '/alerts',
                          onTap: () => context.go('/alerts'),
                        ),
                        const SizedBox(height: 4),
                        _NavItem(
                          icon: Icons.person_outline,
                          activeIcon: Icons.person,
                          label: 'Profile',
                          isActive: widget.currentRoute == '/profile',
                          onTap: () => context.go('/profile'),
                        ),

                        const Spacer(),

                        // ── Divider ──
                        Divider(
                          color: AppColors.outlineVariant.withValues(alpha: 0.3),
                          height: 1,
                        ),
                        const SizedBox(height: 12),

                        // ── Logout row ──
                        InkWell(
                          borderRadius: BorderRadius.circular(12),
                          onTap: () async {
                            final auth = AuthService();
                            final router = GoRouter.of(context);
                            await auth.logout();
                            if (mounted) router.go('/login');
                          },
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 10,
                            ),
                            child: Row(
                              children: [
                                Container(
                                  width: 36,
                                  height: 36,
                                  decoration: BoxDecoration(
                                    color: AppColors.errorContainer.withValues(alpha: 0.5),
                                    shape: BoxShape.circle,
                                  ),
                                  child: const Center(
                                    child: Icon(Icons.logout, color: AppColors.error, size: 20),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'Log Out',
                                        style: AppTypography.bodyMedium.copyWith(
                                          fontWeight: FontWeight.w500,
                                          color: AppColors.error,
                                          fontSize: 14,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),

        // ── Main content ──
        Expanded(child: widget.child),
      ],
    );
  }
}

/// A single navigation item in the sidebar.
class _NavItem extends StatefulWidget {
  final IconData icon;
  final IconData activeIcon;
  final String label;
  final bool isActive;
  final VoidCallback onTap;

  const _NavItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.isActive,
    required this.onTap,
  });

  @override
  State<_NavItem> createState() => _NavItemState();
}

class _NavItemState extends State<_NavItem> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: AnimatedContainer(
        duration: AppAnimations.buttonPress,
        decoration: BoxDecoration(
          color: widget.isActive
              ? AppColors.primaryContainer.withValues(alpha: 0.5)
              : _hovered
                  ? AppColors.surfaceContainerHigh.withValues(alpha: 0.5)
                  : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: widget.onTap,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Row(
              children: [
                Icon(
                  widget.isActive ? widget.activeIcon : widget.icon,
                  color: widget.isActive
                      ? AppColors.primary
                      : AppColors.onSurfaceVariant,
                  size: 22,
                ),
                const SizedBox(width: 14),
                Text(
                  widget.label,
                  style: AppTypography.bodyMedium.copyWith(
                    color: widget.isActive
                        ? AppColors.primary
                        : AppColors.onSurfaceVariant,
                    fontWeight:
                        widget.isActive ? FontWeight.w600 : FontWeight.w400,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
