import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/services/notification_service.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/shimmer_loading.dart';

class AlertsScreen extends StatefulWidget {
  const AlertsScreen({super.key});

  @override
  State<AlertsScreen> createState() => _AlertsScreenState();
}

class _AlertsScreenState extends State<AlertsScreen> {
  final NotificationService _notifService = NotificationService();
  List<NotificationResponse>? _notifications;
  String? _error;
  bool _markingAll = false;

  @override
  void initState() {
    super.initState();
    _loadNotifications();
  }

  Future<void> _loadNotifications() async {
    setState(() {
      _error = null;
    });
    try {
      final list = await _notifService.getAllNotifications();
      if (mounted) setState(() => _notifications = list);
    } catch (e) {
      if (mounted) setState(() => _error = 'Could not load notifications.');
    }
  }

  Future<void> _markAllAsRead() async {
    setState(() => _markingAll = true);
    try {
      await _notifService.markAllAsRead();
      // Refresh list
      await _loadNotifications();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to mark all as read.',
                style: AppTypography.caption
                    .copyWith(color: AppColors.onError)),
            backgroundColor: AppColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _markingAll = false);
    }
  }

  Future<void> _markAsRead(NotificationResponse notif) async {
    if (notif.read == true) return;
    try {
      await _notifService.markAsRead(notif.notificationId!);
      await _loadNotifications();
    } catch (_) {}
  }

  void _onNotificationTap(NotificationResponse notif) {
    _markAsRead(notif);
    if (notif.complaintId != null) {
      context.push('/complaint-detail/${notif.complaintId}');
    }
  }

  int get _unreadCount =>
      _notifications?.where((n) => n.read != true).length ?? 0;

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/alerts',
        child: _buildDesktop(context),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: _buildAppBar(),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
                maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: _buildBody(),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: _buildAppBar(isDesktop: true),
      body: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(
              maxWidth: ResponsiveLayout.contentMaxWidth(context)),
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: ResponsiveLayout.horizontalPadding(context),
            ),
            child: _buildBody(),
          ),
        ),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar({bool isDesktop = false}) {
    return AppBar(
      backgroundColor: AppColors.surface.withValues(alpha: 0.9),
      elevation: 0,
      scrolledUnderElevation: 4,
      shadowColor: Colors.black.withValues(alpha: 0.2),
      centerTitle: !isDesktop,
      title: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            'Notifications',
            style:
                AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
          ),
          if (_unreadCount > 0) ...[
            const SizedBox(width: 8),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(100),
              ),
              child: Text(
                '$_unreadCount',
                style: AppTypography.caption.copyWith(
                  color: AppColors.onPrimary,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ],
      ),
      actions: [
        if (_unreadCount > 0)
          _markingAll
              ? const Padding(
                  padding: EdgeInsets.all(12),
                  child: SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                )
              : IconButton(
                  tooltip: 'Mark all as read',
                  icon: const Icon(Icons.done_all,
                      color: AppColors.primary),
                  onPressed: _markAllAsRead,
                ),
      ],
    );
  }

  Widget _buildBody() {
    // Loading
    if (_notifications == null && _error == null) {
      return ShimmerLoading(
        child: ListView.separated(
          padding: const EdgeInsets.all(AppSpacing.space3),
          itemCount: 6,
          separatorBuilder: (_, _) =>
              const SizedBox(height: AppSpacing.space2),
          itemBuilder: (_, _) => Container(
            height: 80,
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
            ),
          ),
        ),
      );
    }

    // Error
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline,
                size: 48, color: AppColors.error),
            const SizedBox(height: AppSpacing.space2),
            Text(_error!,
                style: AppTypography.bodyMedium
                    .copyWith(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: AppSpacing.space3),
            TextButton(
                onPressed: _loadNotifications,
                child: const Text('Retry')),
          ],
        ),
      );
    }

    // Empty state
    if (_notifications!.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.notifications_none,
                size: 64,
                color: AppColors.onSurfaceVariant.withValues(alpha: 0.4)),
            const SizedBox(height: AppSpacing.space3),
            Text(
              'No notifications yet',
              style: AppTypography.titleLarge.copyWith(
                  color: AppColors.onSurfaceVariant),
            ),
            const SizedBox(height: AppSpacing.space1),
            Text(
              'You\'ll be notified about your complaint updates here.',
              style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.onSurfaceVariant
                      .withValues(alpha: 0.7)),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      );
    }

    // Notification list
    return RefreshIndicator(
      onRefresh: _loadNotifications,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.space3,
          vertical: AppSpacing.space3,
        ),
        itemCount: _notifications!.length,
        separatorBuilder: (_, _) =>
            const SizedBox(height: AppSpacing.space2),
        itemBuilder: (context, index) {
          final notif = _notifications![index];
          return _NotificationTile(
            notification: notif,
            onTap: () => _onNotificationTap(notif),
          );
        },
      ),
    );
  }
}

// ──────────────────────────────────────────────
// Notification Tile
// ──────────────────────────────────────────────

class _NotificationTile extends StatefulWidget {
  final NotificationResponse notification;
  final VoidCallback onTap;

  const _NotificationTile({
    required this.notification,
    required this.onTap,
  });

  @override
  State<_NotificationTile> createState() => _NotificationTileState();
}

class _NotificationTileState extends State<_NotificationTile> {
  bool _hovered = false;

  IconData _iconForType(String? type) {
    switch (type?.toUpperCase()) {
      case 'STATUS_UPDATE':
        return Icons.sync;
      case 'ASSIGNMENT':
        return Icons.person_add;
      case 'RESOLUTION':
        return Icons.check_circle;
      case 'FEEDBACK':
        return Icons.star;
      default:
        return Icons.notifications;
    }
  }

  Color _colorForType(String? type) {
    switch (type?.toUpperCase()) {
      case 'STATUS_UPDATE':
        return AppColors.primary;
      case 'ASSIGNMENT':
        return AppColors.secondary;
      case 'RESOLUTION':
        return const Color(0xFF2E7D32); // green
      case 'FEEDBACK':
        return const Color(0xFFF9A825); // amber
      default:
        return AppColors.onSurfaceVariant;
    }
  }

  String _timeAgo(String? createdAt) {
    if (createdAt == null) return '';
    try {
      final dt = DateTime.parse(createdAt);
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 1) return 'Just now';
      if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      if (diff.inDays < 7) return '${diff.inDays}d ago';
      return '${dt.day}/${dt.month}/${dt.year}';
    } catch (_) {
      return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final n = widget.notification;
    final isUnread = n.read != true;

    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: AnimatedContainer(
        duration: AppAnimations.buttonPress,
        decoration: BoxDecoration(
          color: isUnread
              ? AppColors.primaryContainer.withValues(alpha: 0.15)
              : _hovered
                  ? AppColors.surfaceContainerHigh
                      .withValues(alpha: 0.3)
                  : AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: isUnread
                ? AppColors.primary.withValues(alpha: 0.25)
                : AppColors.outlineVariant.withValues(alpha: 0.2),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.03),
              blurRadius: 6,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(16),
          onTap: widget.onTap,
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.space3),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Icon
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: _colorForType(n.type).withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    _iconForType(n.type),
                    color: _colorForType(n.type),
                    size: 20,
                  ),
                ),
                const SizedBox(width: AppSpacing.space3),
                // Content
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        n.title ?? 'Notification',
                        style: AppTypography.bodyMedium.copyWith(
                          fontWeight:
                              isUnread ? FontWeight.w600 : FontWeight.w400,
                          color: AppColors.onSurface,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        n.message ?? '',
                        style: AppTypography.caption.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      if (n.complaintNo != null) ...[
                        const SizedBox(height: 4),
                        Text(
                          n.complaintNo!,
                          style: AppTypography.caption.copyWith(
                            color: AppColors.primary,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
                const SizedBox(width: AppSpacing.space2),
                // Time & unread dot
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      _timeAgo(n.createdAt),
                      style: AppTypography.caption.copyWith(
                        color: AppColors.onSurfaceVariant,
                        fontSize: 11,
                      ),
                    ),
                    if (isUnread) ...[
                      const SizedBox(height: 6),
                      Container(
                        width: 8,
                        height: 8,
                        decoration: const BoxDecoration(
                          color: AppColors.primary,
                          shape: BoxShape.circle,
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
