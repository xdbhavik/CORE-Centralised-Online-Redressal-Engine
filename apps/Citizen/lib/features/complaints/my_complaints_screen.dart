import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/services/complaint_service.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/shimmer_loading.dart';
import '../../core/widgets/status_badge.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/app_pressable.dart';

/// My Complaints Screen — lists all complaints filed by the logged-in citizen.
/// Supports global search ([initialQuery]) and status filtering
/// ([initialStatus]: 'ASSIGNED' or 'RESOLVED') via route query params.
class MyComplaintsScreen extends StatefulWidget {
  final String? initialQuery;
  final String? initialStatus;

  const MyComplaintsScreen({super.key, this.initialQuery, this.initialStatus});

  @override
  State<MyComplaintsScreen> createState() => _MyComplaintsScreenState();
}

class _MyComplaintsScreenState extends State<MyComplaintsScreen>
    with SingleTickerProviderStateMixin {
  final ComplaintService _service = ComplaintService();
  late AnimationController _entranceCtrl;

  List<ComplaintResponse>? _complaints;
  String? _error;

  // Search & filter state
  final TextEditingController _searchCtrl = TextEditingController();
  String _query = '';
  String? _statusFilter;

  @override
  void initState() {
    super.initState();
    _query = widget.initialQuery?.trim() ?? '';
    _searchCtrl.text = _query;
    final s = widget.initialStatus?.toUpperCase();
    if (s == 'ASSIGNED' || s == 'RESOLVED') _statusFilter = s;
    _entranceCtrl = AnimationController(
      vsync: this,
      duration: AppAnimations.cardEntrance,
    );
    _entranceCtrl.forward();
    _loadComplaints();
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    _entranceCtrl.dispose();
    super.dispose();
  }

  /// Complaints after applying the search query and status filter.
  List<ComplaintResponse> get _visibleComplaints {
    final all = _complaints ?? const <ComplaintResponse>[];
    return all.where((c) {
      if (_statusFilter != null) {
        final s = c.status.toUpperCase();
        if (_statusFilter == 'ASSIGNED' &&
            s != 'ASSIGNED' &&
            s != 'IN_PROGRESS') {
          return false;
        }
        if (_statusFilter == 'RESOLVED' &&
            s != 'RESOLVED' &&
            s != 'CLOSED') {
          return false;
        }
      }
      if (_query.isNotEmpty) {
        final q = _query.toLowerCase();
        final haystack =
            '${c.title ?? ''} ${c.complaintNumber} ${c.category ?? ''} ${c.status}'
                .toLowerCase();
        if (!haystack.contains(q)) return false;
      }
      return true;
    }).toList();
  }

  void _onQueryChanged(String value) {
    setState(() => _query = value.trim());
  }

  void _clearSearchAndFilter() {
    setState(() {
      _query = '';
      _searchCtrl.clear();
      _statusFilter = null;
    });
  }

  Future<void> _loadComplaints() async {
    try {
      final complaints = await _service.getMyComplaints();
      if (mounted) {
        setState(() {
          _complaints = complaints;
          _error = null;
        });
        _entranceCtrl.forward();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = 'Could not load complaints. Pull down to retry.');
      }
    }
  }

  StatusBadgeType _statusToBadgeType(String status) {
    switch (status.toUpperCase()) {
      case 'REGISTERED':
      case 'AI_ANALYZED':
        return StatusBadgeType.pending;
      case 'ASSIGNED':
      case 'IN_PROGRESS':
        return StatusBadgeType.active;
      case 'RESOLVED':
      case 'CLOSED':
        return StatusBadgeType.resolved;
      default:
        return StatusBadgeType.pending;
    }
  }

  String _formatDate(String iso) {
    try {
      final dt = DateTime.parse(iso).toLocal();
      const months = [
        'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
        'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
      ];
      return '${dt.day} ${months[dt.month - 1]} ${dt.year}';
    } catch (_) {
      return '';
    }
  }

  String _formatStatus(String status) {
    switch (status.toUpperCase()) {
      case 'REGISTERED':
        return 'Registered';
      case 'AI_ANALYZED':
        return 'AI Analyzed';
      case 'ASSIGNED':
        return 'Assigned';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'RESOLVED':
        return 'Resolved';
      case 'CLOSED':
        return 'Closed';
      default:
        return status;
    }
  }

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/my-complaints',
        child: _buildDesktop(context),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: _buildAppBar(),
      body: _buildBody(context),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: _buildAppBar(isDesktop: true),
      body: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: ResponsiveLayout.contentMaxWidth(context)),
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: ResponsiveLayout.horizontalPadding(context),
            ),
            child: _buildBody(context, isDesktop: true),
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
      leading: isDesktop
          ? null
          : IconButton(
              icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
              onPressed: () {
                if (GoRouter.of(context).canPop()) {
                  context.pop();
                } else {
                  context.go('/home');
                }
              },
            ),
      title: Text(
        'My Complaints',
        style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.refresh, color: AppColors.primary),
          onPressed: () {
            setState(() {
              _complaints = null;
              _error = null;
            });
            _entranceCtrl.reset();
            _loadComplaints();
          },
        ),
        const SizedBox(width: AppSpacing.space1),
      ],
    );
  }

  Widget _buildBody(BuildContext context, {bool isDesktop = false}) {
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.space4),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: AppColors.error),
              const SizedBox(height: AppSpacing.space3),
              Text(
                _error!,
                textAlign: TextAlign.center,
                style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
              ),
              const SizedBox(height: AppSpacing.space4),
              ElevatedButton.icon(
                onPressed: () {
                  setState(() => _error = null);
                  _loadComplaints();
                },
                icon: const Icon(Icons.refresh),
                label: const Text('Retry'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primary,
                  foregroundColor: AppColors.onPrimary,
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_complaints == null) {
      return _buildShimmerList();
    }
    if (_complaints!.isEmpty) {
      return _buildEmptyState();
    }

    final visible = _visibleComplaints;

    return Column(
      children: [
        _buildSearchBar(),
        if (_statusFilter != null) _buildStatusFilterChip(),
        Expanded(
          child: visible.isEmpty
              ? _buildNoResults()
              : RefreshIndicator(
                  onRefresh: _loadComplaints,
                  color: AppColors.primary,
                  backgroundColor: AppColors.surfaceContainerHighest,
                  child: isDesktop
                      ? _buildDesktopGrid(visible)
                      : _buildMobileList(visible),
                ),
        ),
      ],
    );
  }

  Widget _buildSearchBar() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.containerMarginMobile,
        AppSpacing.space2,
        AppSpacing.containerMarginMobile,
        0,
      ),
      child: Focus(
        child: Builder(builder: (context) {
          final focused = Focus.of(context).hasFocus;
          return AnimatedContainer(
            duration: AppAnimations.buttonPress,
        height: 48,
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(100),
          border: Border.all(
            color: focused ? AppColors.primary : AppColors.outlineVariant,
            width: focused ? 2 : 1,
          ),
          boxShadow: focused
              ? [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.08),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ]
              : null,
        ),
        child: Row(
          children: [
            const SizedBox(width: 16),
            const Icon(Icons.search, color: AppColors.outlineVariant, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: TextField(
                controller: _searchCtrl,
                onChanged: _onQueryChanged,
                style: AppTypography.bodyMedium
                    .copyWith(color: AppColors.onSurface),
                decoration: InputDecoration(
                  hintText: 'Search by title, number, category, status...',
                  hintStyle: AppTypography.bodyMedium
                      .copyWith(color: AppColors.outline),
                  border: InputBorder.none,
                  isDense: true,
                ),
              ),
            ),
            if (_query.isNotEmpty)
              IconButton(
                icon: const Icon(Icons.close,
                    size: 18, color: AppColors.onSurfaceVariant),
                onPressed: () {
                  _searchCtrl.clear();
                  _onQueryChanged('');
                },
              ),
          ],
        ),
          );
        }),
      ),
    );
  }

  Widget _buildStatusFilterChip() {
    final label =
        _statusFilter == 'ASSIGNED' ? 'Assigned / In Progress' : 'Resolved / Closed';
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.containerMarginMobile,
        AppSpacing.space1,
        AppSpacing.containerMarginMobile,
        0,
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: AppColors.primaryContainer,
              borderRadius: BorderRadius.circular(100),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.filter_list,
                    size: 14, color: AppColors.onPrimaryContainer),
                const SizedBox(width: 4),
                Text(
                  label,
                  style: AppTypography.labelSmall
                      .copyWith(color: AppColors.onPrimaryContainer),
                ),
                const SizedBox(width: 4),
                GestureDetector(
                  onTap: _clearSearchAndFilter,
                  child: const Icon(Icons.close,
                      size: 14, color: AppColors.onPrimaryContainer),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNoResults() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.search_off, size: 48, color: AppColors.outlineVariant),
          const SizedBox(height: AppSpacing.space2),
          Text(
            'No complaints match your search',
            style: AppTypography.bodyMedium
                .copyWith(color: AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: AppSpacing.space1),
          TextButton(
            onPressed: _clearSearchAndFilter,
            child: Text(
              'Clear search & filters',
              style: AppTypography.bodyMedium.copyWith(color: AppColors.primary),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(
              color: AppColors.primaryContainer,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.inbox,
              color: AppColors.onPrimaryContainer,
              size: 32,
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'No complaints yet',
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space1),
          Text(
            'File your first complaint from the home screen.',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildMobileList(List<ComplaintResponse> visible) {
    return ListView.builder(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.containerMarginMobile,
        vertical: AppSpacing.space3,
      ),
      itemCount: visible.length,
      itemBuilder: (context, index) {
        final complaint = visible[index];
        return _buildComplaintCard(complaint, index);
      },
    );
  }

  Widget _buildDesktopGrid(List<ComplaintResponse> visible) {
    return GridView.builder(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.space4),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: AppSpacing.space4,
        mainAxisSpacing: AppSpacing.space4,
        mainAxisExtent: 180,
      ),
      itemCount: visible.length,
      itemBuilder: (context, index) {
        final complaint = visible[index];
        return _buildComplaintCard(complaint, index);
      },
    );
  }

  Widget _buildComplaintCard(ComplaintResponse complaint, int index) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: Duration(milliseconds: 300 + (index * 50)),
      curve: AppAnimations.cardEntranceCurve,
      builder: (context, value, child) {
        return Opacity(
          opacity: value,
          child: Transform.translate(
            offset: Offset(0, 12 * (1 - value)),
            child: child,
          ),
        );
      },
      child: AppPressable(
        onPressed: () => context.push('/complaint-detail/${complaint.complaintId}'),
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.space3),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: AppColors.primaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.description,
                      color: AppColors.onPrimaryContainer,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Title (falls back to complaint number if missing)
                        Text(
                          (complaint.title != null && complaint.title!.isNotEmpty)
                              ? complaint.title!
                              : complaint.complaintNumber,
                          style: AppTypography.bodyMedium.copyWith(
                            fontWeight: FontWeight.w600,
                            color: AppColors.onSurface,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        // Number + date row
                        Text(
                          [
                            complaint.complaintNumber,
                            if (complaint.createdAt != null)
                              _formatDate(complaint.createdAt!),
                          ].join(' • '),
                          style: AppTypography.caption.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        if (complaint.category != null &&
                            complaint.category!.isNotEmpty)
                          Text(
                            complaint.category!,
                            style: AppTypography.caption.copyWith(
                              color: AppColors.primary,
                              fontWeight: FontWeight.w500,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  StatusBadge(
                    type: _statusToBadgeType(complaint.status),
                    label: _formatStatus(complaint.status),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(Icons.chevron_right, size: 16, color: AppColors.primary),
                  const SizedBox(width: 4),
                  Text(
                    'View Details',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildShimmerList() {
    return ShimmerLoading(
      child: ListView.separated(
        padding: const EdgeInsets.all(AppSpacing.space2),
        itemCount: 4,
        separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.space2),
        itemBuilder: (_, _) => Container(
          height: 100,
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
    );
  }
}
