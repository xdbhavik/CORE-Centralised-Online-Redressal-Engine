import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/services/complaint_service.dart';
import '../../core/services/feedback_service.dart';
import '../../core/network/dio_client.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/shimmer_loading.dart';
import '../../core/widgets/status_badge.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/complaint_map_widget.dart';

/// Complaint Detail Screen — shows full complaint details and status timeline.
class ComplaintDetailScreen extends StatefulWidget {
  final int complaintId;

  const ComplaintDetailScreen({super.key, required this.complaintId});

  @override
  State<ComplaintDetailScreen> createState() => _ComplaintDetailScreenState();
}

class _ComplaintDetailScreenState extends State<ComplaintDetailScreen>
    with SingleTickerProviderStateMixin {
  final ComplaintService _service = ComplaintService();
  final FeedbackService _feedbackService = FeedbackService();
  late AnimationController _entranceCtrl;
  late Animation<double> _fadeAnim;
  late Animation<Offset> _slideAnim;

  ComplaintDetailsResponse? _details;
  List<TimelineDTO>? _timeline;
  String? _error;
  FeedbackResponse? _existingFeedback;
  bool _feedbackLoading = false;

  @override
  void initState() {
    super.initState();
    _entranceCtrl = AnimationController(
      vsync: this,
      duration: AppAnimations.cardEntrance,
    );
    _fadeAnim = CurvedAnimation(
      parent: _entranceCtrl,
      curve: AppAnimations.cardEntranceCurve,
    );
    _slideAnim = Tween<Offset>(
      begin: const Offset(0, 0.04),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _entranceCtrl,
      curve: AppAnimations.cardEntranceCurve,
    ));
    _loadData();
  }

  @override
  void dispose() {
    _entranceCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadData() async {
    try {
      final results = await Future.wait([
        _service.getComplaintDetails(widget.complaintId),
        _service.getTimeline(widget.complaintId),
      ]);
      if (mounted) {
        setState(() {
          _details = results[0] as ComplaintDetailsResponse;
          _timeline = results[1] as List<TimelineDTO>;
          _error = null;
        });
        _entranceCtrl.forward();
        _loadFeedback();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = 'Could not load complaint details.');
      }
    }
  }

  Future<void> _loadFeedback() async {
    final status = _details?.status.toUpperCase();
    if (status != 'RESOLVED' && status != 'CLOSED') return;
    setState(() => _feedbackLoading = true);
    try {
      final fb = await _feedbackService.getFeedback(widget.complaintId);
      if (mounted) setState(() => _existingFeedback = fb);
    } catch (_) {
      // No feedback yet — that's fine
    } finally {
      if (mounted) setState(() => _feedbackLoading = false);
    }
  }

  bool get _canModify {
    if (_details == null) return false;
    final s = _details!.status.toUpperCase();
    return s != 'RESOLVED' && s != 'CLOSED';
  }

  bool get _isResolved {
    if (_details == null) return false;
    return _details!.status.toUpperCase() == 'RESOLVED';
  }

  bool get _isResolvedOrClosed {
    if (_details == null) return false;
    final s = _details!.status.toUpperCase();
    return s == 'RESOLVED' || s == 'CLOSED';
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
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.surface.withValues(alpha: 0.8),
        elevation: 0,
        shadowColor: Colors.black.withValues(alpha: 0.04),
        surfaceTintColor: Colors.transparent,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
          onPressed: () {
            if (GoRouter.of(context).canPop()) {
              context.pop();
            } else {
              context.go('/my-complaints');
            }
          },
        ),
        title: Text(
          'Complaint Details',
          style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
        ),
        actions: [
          PopupMenuButton<String>(
            icon: const Icon(Icons.more_vert, color: AppColors.onSurface),
            onSelected: (value) {
              if (value == 'delete') _handleDelete();
              if (value == 'close') _handleClose();
              if (value == 'reopen') _handleReopen();
            },
            itemBuilder: (_) => [
              if (_canModify)
                const PopupMenuItem(value: 'delete', child: Text('Delete Complaint')),
              if (_isResolved)
                const PopupMenuItem(value: 'close', child: Text('Close Complaint')),
              if (_isResolved)
                const PopupMenuItem(value: 'reopen', child: Text('Reopen Complaint')),
            ],
          ),
        ],
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: _buildBody(),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space10, vertical: AppSpacing.space6),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back, color: AppColors.onSurface),
                  onPressed: () {
                    if (GoRouter.of(context).canPop()) {
                      context.pop();
                    } else {
                      context.go('/my-complaints');
                    }
                  },
                ),
                const SizedBox(width: AppSpacing.space2),
                Text(
                  'Complaint Details',
                  style: AppTypography.headlineMedium.copyWith(color: AppColors.onSurface),
                ),
                const Spacer(),
                if (_canModify)
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert, color: AppColors.onSurface),
                    onSelected: (value) {
                      if (value == 'delete') _handleDelete();
                    },
                    itemBuilder: (_) => [
                      const PopupMenuItem(value: 'delete', child: Text('Delete Complaint')),
                    ],
                  ),
              ],
            ),
            const SizedBox(height: AppSpacing.space6),
            
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 600),
                  child: _buildBody(),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBody() {
    if (_details == null && _error == null) {
      return _buildShimmer();
    }
    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline, size: 48, color: AppColors.error),
            const SizedBox(height: AppSpacing.space2),
            Text(_error!, style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: AppSpacing.space3),
            TextButton(onPressed: _loadData, child: const Text('Retry')),
          ],
        ),
      );
    }

    return SlideTransition(
      position: _slideAnim,
      child: FadeTransition(
        opacity: _fadeAnim,
        child: RefreshIndicator(
          onRefresh: _loadData,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(AppSpacing.space2),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildHeaderCard(),
                const SizedBox(height: AppSpacing.space3),
                _buildInfoGrid(),
                const SizedBox(height: AppSpacing.space4),
                _buildDescriptionCard(),
                const SizedBox(height: AppSpacing.space4),
                _buildMediaSection(),
                if (_details?.latitude != null && _details?.longitude != null) ...[
                  Text('Location', style: AppTypography.titleLarge),
                  const SizedBox(height: AppSpacing.space2),
                  ComplaintMapWidget(
                    initialLatitude: _details!.latitude,
                    initialLongitude: _details!.longitude,
                    interactive: false,
                    height: 180,
                  ),
                  const SizedBox(height: AppSpacing.space4),
                ],
                _buildTimelineSection(),
                const SizedBox(height: AppSpacing.space4),
                if (_isResolvedOrClosed) _buildFeedbackSection(),
                const SizedBox(height: AppSpacing.space5),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderCard() {
    final d = _details!;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Stack(
        children: [
          // Decorative gradient blob
          Positioned(
            top: -30,
            right: -30,
            child: Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: RadialGradient(
                  colors: [
                    AppColors.primaryFixedDim.withValues(alpha: 0.2),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      d.complaintNumber,
                      style: AppTypography.titleLarge.copyWith(
                        fontWeight: FontWeight.w700,
                        color: AppColors.onSurface,
                        letterSpacing: -0.3,
                      ),
                    ),
                  ),
                  StatusBadge(
                    type: _statusToBadgeType(d.status),
                    label: _formatStatus(d.status),
                  ),
                ],
              ),
              if (d.title != null && d.title!.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  d.title!,
                  style: AppTypography.headlineMedium.copyWith(
                    fontSize: 20,
                    fontWeight: FontWeight.w600,
                    color: AppColors.onSurface,
                  ),
                ),
              ],
              if (d.createdAt != null) ...[
                const SizedBox(height: 8),
                Row(
                  children: [
                    Icon(Icons.access_time, size: 14, color: AppColors.onSurfaceVariant),
                    const SizedBox(width: 4),
                    Text(
                      _formatDate(d.createdAt!),
                      style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
                    ),
                  ],
                ),
              ],
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildInfoGrid() {
    final d = _details!;
    return Row(
      children: [
        if (d.department != null)
          Expanded(child: _buildInfoTile(Icons.account_balance, 'Department', d.department!)),
        if (d.department != null && d.category != null)
          const SizedBox(width: AppSpacing.space2),
        if (d.category != null)
          Expanded(child: _buildInfoTile(Icons.category, 'Category', d.category!)),
        if ((d.department != null || d.category != null) && d.priority != null)
          const SizedBox(width: AppSpacing.space2),
        if (d.priority != null)
          Expanded(child: _buildInfoTile(Icons.flag, 'Priority', d.priority!)),
      ],
    );
  }

  Widget _buildInfoTile(IconData icon, String label, String value) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 4,
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: AppColors.onSurfaceVariant),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  label,
                  style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: AppTypography.bodyMedium.copyWith(
              fontWeight: FontWeight.w500,
              color: AppColors.onSurface,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildDescriptionCard() {
    final d = _details!;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Description', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space2),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(AppSpacing.space3),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(16),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.03),
                blurRadius: 6,
              ),
            ],
          ),
          child: Text(
            d.description,
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurface,
              height: 1.6,
            ),
          ),
        ),
        if (d.officer != null) ...[
          const SizedBox(height: AppSpacing.space3),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: AppColors.secondaryContainer.withValues(alpha: 0.5),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                const Icon(Icons.person, color: AppColors.secondary, size: 20),
                const SizedBox(width: 8),
                Text(
                  'Assigned to: ${d.officer}',
                  style: AppTypography.labelSmall.copyWith(color: AppColors.onSecondaryContainer),
                ),
              ],
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildTimelineSection() {
    if (_timeline == null || _timeline!.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Status Timeline', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space3),
        ...List.generate(_timeline!.length, (i) {
          final entry = _timeline![i];
          final isLast = i == _timeline!.length - 1;
          final isFirst = i == 0;
          return IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 24,
                  child: Column(
                    children: [
                      Container(
                        width: 16,
                        height: 16,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: isFirst ? AppColors.primary : AppColors.surfaceContainerHigh,
                          border: Border.all(color: AppColors.background, width: 3),
                          boxShadow: isFirst
                              ? [
                                  BoxShadow(
                                    color: AppColors.primary.withValues(alpha: 0.5),
                                    blurRadius: 10,
                                  ),
                                ]
                              : null,
                        ),
                      ),
                      if (!isLast)
                        Expanded(
                          child: Container(width: 2, color: AppColors.outlineVariant),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(bottom: isLast ? 0 : 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _formatStatus(entry.status),
                          style: AppTypography.bodyMedium.copyWith(
                            fontWeight: isFirst ? FontWeight.w600 : FontWeight.w400,
                            color: isFirst ? AppColors.onSurface : AppColors.onSurfaceVariant,
                          ),
                        ),
                        if (entry.remarks != null)
                          Text(
                            entry.remarks!,
                            style: AppTypography.caption.copyWith(
                              color: isFirst ? AppColors.onSurfaceVariant : AppColors.outline,
                            ),
                          ),
                        if (entry.time != null)
                          Text(
                            _formatDate(entry.time!),
                            style: AppTypography.caption.copyWith(
                              color: AppColors.outline,
                              fontSize: 11,
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          );
        }),
      ],
    );
  }

  Widget _buildShimmer() {
    return ShimmerLoading(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.space2),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(height: 120, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(20))),
            const SizedBox(height: 16),
            Row(children: [
              Expanded(child: Container(height: 70, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)))),
              const SizedBox(width: 12),
              Expanded(child: Container(height: 70, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16)))),
            ]),
            const SizedBox(height: 16),
            Container(height: 100, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(16))),
          ],
        ),
      ),
    );
  }

  Future<void> _handleDelete() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Complaint?'),
        content: const Text('This action cannot be undone. The complaint will be removed from your records.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Delete', style: TextStyle(color: AppColors.error)),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await _service.deleteComplaint(widget.complaintId);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Complaint deleted.')),
          );
          context.go('/my-complaints');
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Failed to delete complaint.'),
              backgroundColor: AppColors.error,
            ),
          );
        }
      }
    }
  }

  Future<void> _handleClose() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Close Complaint?'),
        content: const Text('You are confirming that the issue has been resolved satisfactorily.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Close'),
          ),
        ],
      ),
    );
    if (confirm == true) {
      try {
        await _service.closeComplaint(widget.complaintId);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Complaint closed.')),
          );
          _loadData();
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Failed to close complaint.'),
              backgroundColor: AppColors.error,
            ),
          );
        }
      }
    }
  }

  Future<void> _handleReopen() async {
    final reasonCtrl = TextEditingController();
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reopen Complaint'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Please explain why this complaint needs to be reopened:'),
            const SizedBox(height: 12),
            TextField(
              controller: reasonCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Reason for reopening...',
                border: OutlineInputBorder(),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: Text('Reopen', style: TextStyle(color: AppColors.error)),
          ),
        ],
      ),
    );
    if (confirm == true && reasonCtrl.text.trim().isNotEmpty) {
      try {
        await _service.reopenComplaint(widget.complaintId, reasonCtrl.text.trim());
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Complaint reopened.')),
          );
          _loadData();
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: const Text('Failed to reopen complaint.'),
              backgroundColor: AppColors.error,
            ),
          );
        }
      }
    }
    reasonCtrl.dispose();
  }

  void _showFeedbackSheet() {
    int selectedRating = 0;
    final commentsCtrl = TextEditingController();
    bool submitting = false;

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setModalState) {
            return Padding(
              padding: EdgeInsets.fromLTRB(
                AppSpacing.space4,
                AppSpacing.space4,
                AppSpacing.space4,
                MediaQuery.of(ctx).viewInsets.bottom + AppSpacing.space4,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.outlineVariant,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.space4),
                  Text('Rate Your Experience',
                      style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface)),
                  const SizedBox(height: AppSpacing.space1),
                  Text('How satisfied are you with the resolution?',
                      style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
                  const SizedBox(height: AppSpacing.space4),
                  // Star rating
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: List.generate(5, (i) {
                      final starIndex = i + 1;
                      return GestureDetector(
                        onTap: () => setModalState(() => selectedRating = starIndex),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6),
                          child: Icon(
                            starIndex <= selectedRating ? Icons.star : Icons.star_border,
                            color: starIndex <= selectedRating
                                ? const Color(0xFFF9A825)
                                : AppColors.onSurfaceVariant,
                            size: 40,
                          ),
                        ),
                      );
                    }),
                  ),
                  const SizedBox(height: AppSpacing.space4),
                  TextField(
                    controller: commentsCtrl,
                    maxLines: 3,
                    decoration: InputDecoration(
                      hintText: 'Additional comments (optional)',
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.space4),
                  SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: selectedRating == 0 || submitting
                          ? null
                          : () async {
                              setModalState(() => submitting = true);
                              try {
                                await _feedbackService.submitFeedback(
                                  widget.complaintId,
                                  selectedRating,
                                  comments: commentsCtrl.text.trim().isNotEmpty
                                      ? commentsCtrl.text.trim()
                                      : null,
                                );
                                if (ctx.mounted) {
                                  Navigator.pop(ctx);
                                }
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    const SnackBar(content: Text('Feedback submitted. Thank you!')),
                                  );
                                  _loadFeedback();
                                }
                              } catch (e) {
                                setModalState(() => submitting = false);
                                if (mounted) {
                                  ScaffoldMessenger.of(context).showSnackBar(
                                    SnackBar(
                                      content: const Text('Failed to submit feedback.'),
                                      backgroundColor: AppColors.error,
                                    ),
                                  );
                                }
                              }
                            },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primary,
                        foregroundColor: AppColors.onPrimary,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: submitting
                          ? const SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: AppColors.onPrimary,
                              ),
                            )
                          : const Text('Submit Feedback'),
                    ),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildFeedbackSection() {
    if (_feedbackLoading) {
      return const Center(child: Padding(
        padding: EdgeInsets.all(AppSpacing.space4),
        child: CircularProgressIndicator(),
      ));
    }

    if (_existingFeedback != null) {
      // Show existing feedback
      return Container(
        padding: const EdgeInsets.all(AppSpacing.space3),
        decoration: BoxDecoration(
          color: const Color(0xFFF9A825).withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFF9A825).withValues(alpha: 0.3)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.reviews, color: Color(0xFFF9A825), size: 20),
                const SizedBox(width: 8),
                Text('Your Feedback',
                    style: AppTypography.bodyMedium.copyWith(
                        fontWeight: FontWeight.w600, color: AppColors.onSurface)),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: List.generate(5, (i) {
                return Icon(
                  i < (_existingFeedback!.rating ?? 0) ? Icons.star : Icons.star_border,
                  color: const Color(0xFFF9A825),
                  size: 24,
                );
              }),
            ),
            if (_existingFeedback!.comments != null && _existingFeedback!.comments!.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                _existingFeedback!.comments!,
                style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
              ),
            ],
          ],
        ),
      );
    }

    // Prompt to submit feedback
    return InkWell(
      borderRadius: BorderRadius.circular(16),
      onTap: _showFeedbackSheet,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.space3),
        decoration: BoxDecoration(
          color: AppColors.primaryContainer.withValues(alpha: 0.3),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.primary.withValues(alpha: 0.3)),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: const Icon(Icons.star_rate, color: AppColors.primary, size: 22),
            ),
            const SizedBox(width: AppSpacing.space3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Rate this resolution',
                      style: AppTypography.bodyMedium.copyWith(
                          fontWeight: FontWeight.w600, color: AppColors.onSurface)),
                  Text('Help us improve by sharing your experience.',
                      style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant)),
                ],
              ),
            ),
            const Icon(Icons.chevron_right, color: AppColors.primary),
          ],
        ),
      ),
    );
  }

  Widget _buildMediaSection() {
    if (_details == null || _details!.mediaUrls.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Attachments', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space3),
        SizedBox(
          height: 120,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _details!.mediaUrls.length,
            separatorBuilder: (_, _) => const SizedBox(width: AppSpacing.space2),
            itemBuilder: (context, index) {
              // Backend returns relative paths like /media/{id}/{file} —
              // resolve them against the server base URL before loading.
              final url = DioClient().resolveMediaUrl(_details!.mediaUrls[index]);
              final lower = url.toLowerCase();
              final isImage = lower.endsWith('.jpg') ||
                  lower.endsWith('.png') ||
                  lower.endsWith('.jpeg');

              return GestureDetector(
                onTap: isImage ? () => _showMediaPreview(url) : null,
                child: Container(
                  width: 120,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainerHigh,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                        color: AppColors.outlineVariant.withValues(alpha: 0.5)),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: isImage
                      ? Image.network(
                          url,
                          fit: BoxFit.cover,
                          loadingBuilder: (context, child, progress) {
                            if (progress == null) return child;
                            return const Center(
                              child: SizedBox(
                                width: 24,
                                height: 24,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: AppColors.primary,
                                ),
                              ),
                            );
                          },
                          errorBuilder: (context, error, stackTrace) =>
                              const Center(
                            child: Icon(Icons.broken_image_outlined,
                                color: AppColors.onSurfaceVariant, size: 32),
                          ),
                        )
                      : const Center(
                          child: Icon(Icons.insert_drive_file,
                              color: AppColors.onSurfaceVariant, size: 32),
                        ),
                ),
              );
            },
          ),
        ),
        const SizedBox(height: AppSpacing.space4),
      ],
    );
  }

  /// Full-screen image preview with pinch-to-zoom.
  void _showMediaPreview(String url) {
    showDialog<void>(
      context: context,
      barrierColor: Colors.black.withValues(alpha: 0.9),
      builder: (ctx) => Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: EdgeInsets.zero,
        child: Stack(
          children: [
            InteractiveViewer(
              minScale: 0.5,
              maxScale: 4.0,
              child: Center(
                child: Image.network(
                  url,
                  fit: BoxFit.contain,
                  loadingBuilder: (context, child, progress) {
                    if (progress == null) return child;
                    return const Center(
                      child: CircularProgressIndicator(
                        color: AppColors.onPrimary,
                      ),
                    );
                  },
                  errorBuilder: (context, error, stackTrace) => const Center(
                    child: Icon(Icons.broken_image_outlined,
                        color: Colors.white70, size: 48),
                  ),
                ),
              ),
            ),
            Positioned(
              top: 16,
              right: 16,
              child: IconButton(
                icon: const Icon(Icons.close, color: Colors.white, size: 28),
                onPressed: () => Navigator.of(ctx).pop(),
              ),
            ),
          ],
        ),
      ),
    );
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

  String _formatDate(String isoDate) {
    try {
      final dt = DateTime.parse(isoDate);
      final months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return '${dt.day} ${months[dt.month - 1]} ${dt.year}, ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return isoDate;
    }
  }
}
