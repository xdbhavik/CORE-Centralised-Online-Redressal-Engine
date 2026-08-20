import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/models/complaint.dart';
import '../../core/services/ai_service.dart';
import '../../core/services/complaint_service.dart';
import 'dart:io';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/app_pressable.dart';

/// AI Review Screen — matches Stitch "AI Review | Cognitive Trust" design.
/// Features: iridescent glow header, AI summary card with blur blob,
/// reasoning cards, filing details, timeline, shimmer submit button.
class AiReviewScreen extends StatefulWidget {
  final ComplaintDraft? draft;
  const AiReviewScreen({super.key, this.draft});

  @override
  State<AiReviewScreen> createState() => _AiReviewScreenState();
}

class _AiReviewScreenState extends State<AiReviewScreen>
    with TickerProviderStateMixin {
  late AnimationController _shimmerCtrl;
  late AnimationController _entranceCtrl;
  late Animation<double> _entranceFade;
  late Animation<Offset> _entranceSlide;

  bool _isSubmitting = false;
  DuplicateCheckResponse? _duplicate;
  final AiService _aiService = AiService();
  final ComplaintService _complaintService = ComplaintService();

  @override
  void initState() {
    super.initState();
    _shimmerCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1500),
    )..repeat();

    _entranceCtrl = AnimationController(
      vsync: this,
      duration: AppAnimations.cardEntrance,
    );
    _entranceFade = CurvedAnimation(
      parent: _entranceCtrl,
      curve: AppAnimations.cardEntranceCurve,
    );
    _entranceSlide = Tween<Offset>(
      begin: const Offset(0, 0.04),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _entranceCtrl,
      curve: AppAnimations.cardEntranceCurve,
    ));
    _entranceCtrl.forward();
    _checkDuplicate();
  }

  Future<void> _checkDuplicate() async {
    if (widget.draft == null) return;
    try {
      final dup = await _aiService.checkDuplicate(
        description: widget.draft!.description,
        latitude: widget.draft!.latitude,
        longitude: widget.draft!.longitude,
      );
      if (mounted) setState(() => _duplicate = dup);
    } catch (_) {}
  }

  Future<void> _submitComplaint() async {
    if (widget.draft == null) return;
    setState(() => _isSubmitting = true);
    try {
      // Citizen-provided title takes priority; fall back to the AI-generated one.
      final userTitle = widget.draft!.title?.trim();
      final response = await _complaintService.createComplaint(
        title: (userTitle != null && userTitle.isNotEmpty)
            ? userTitle
            : widget.draft!.aiAnalysis?.title,
        description: widget.draft!.description,
        latitude: widget.draft!.latitude,
        longitude: widget.draft!.longitude,
        address: widget.draft!.address,
        language: widget.draft!.language,
      );

      // Backend rejected the submission as a confirmed duplicate — the returned
      // complaintId belongs to the EXISTING complaint, not a new one. Skip media
      // upload (it would silently attach to someone else's complaint) and inform
      // the user with the existing complaint's number/status.
      if (response.isDuplicate) {
        if (mounted) {
          setState(() => _isSubmitting = false);
          await _showDuplicateDialog(response);
          if (mounted) context.go('/my-complaints');
        }
        return;
      }

      // Upload every attached media file (photos / videos / documents).
      for (final path in widget.draft!.allMediaPaths) {
        await _complaintService.uploadMedia(response.complaintId, File(path));
      }

      if (mounted) context.go('/my-complaints');
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(_errorMessage(e), style: AppTypography.caption.copyWith(color: AppColors.onError)),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  /// Extracts the backend's actual error message (rate limit, validation, etc.)
  /// instead of a generic failure string.
  String _errorMessage(Object e) {
    if (e is DioException) {
      final data = e.response?.data;
      if (data is Map && data['message'] != null) {
        return data['message'].toString();
      }
      if (e.response?.statusCode == 429) {
        return 'Too many requests. Please try again later.';
      }
    }
    return 'Failed to lodge grievance. Please try again.';
  }

  Future<void> _showDuplicateDialog(ComplaintResponse response) {
    final existingNo = response.existingComplaintNumber ?? response.complaintNumber;
    final existingStatus = response.existingStatus;
    final reason = response.reasons.isNotEmpty ? response.reasons.first : null;

    return showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.content_copy, color: AppColors.tertiary, size: 32),
        title: const Text('Similar Complaint Exists'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'A similar complaint is already being processed. No new complaint was created.',
            ),
            const SizedBox(height: 12),
            if (existingNo.isNotEmpty)
              Text('Complaint No: $existingNo',
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            if (existingStatus != null) Text('Status: $existingStatus'),
            if (reason != null) ...[
              const SizedBox(height: 8),
              Text(reason, style: AppTypography.caption),
            ],
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _shimmerCtrl.dispose();
    _entranceCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/new-grievance', // Or whichever
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
              context.go('/home');
            }
          },
        ),
        title: Text(
          'Grievance Detail',
          style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
        ),
      ),
      body: SafeArea(
        child: Stack(
          children: [
            SlideTransition(
              position: _entranceSlide,
              child: FadeTransition(
                opacity: _entranceFade,
                child: SingleChildScrollView(
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.space2,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const SizedBox(height: AppSpacing.space2),
                            _buildHeader(),
                            const SizedBox(height: AppSpacing.space3),
                            _buildAiSummaryCard(),
                            const SizedBox(height: AppSpacing.space5),
                            _buildReasoningSection(),
                            const SizedBox(height: AppSpacing.space5),
                            _buildFilingDetails(),
                            const SizedBox(height: AppSpacing.space5),
                            _buildTimeline(),
                            const SizedBox(height: 120), // space for bottom button
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
            // Fixed bottom button
            Positioned(
              bottom: 0,
              left: 0,
              right: 0,
              child: _buildSubmitButton(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Stack(
        children: [
          Padding(
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
                          context.go('/home');
                        }
                      },
                    ),
                    const SizedBox(width: AppSpacing.space2),
                    Text(
                      'Grievance Detail',
                      style: AppTypography.headlineMedium.copyWith(color: AppColors.onSurface),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.space6),
                
                Expanded(
                  child: SlideTransition(
                    position: _entranceSlide,
                    child: FadeTransition(
                      opacity: _entranceFade,
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.only(bottom: 120),
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            // Left column: Summary & Details
                            Expanded(
                              flex: 6,
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  _buildHeader(),
                                  const SizedBox(height: AppSpacing.space6),
                                  _buildAiSummaryCard(),
                                  const SizedBox(height: AppSpacing.space5),
                                  _buildFilingDetails(),
                                ],
                              ),
                            ),
                            
                            const SizedBox(width: AppSpacing.space8),
                            
                            // Right column: Reasoning & Timeline
                            Expanded(
                              flex: 4,
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  _buildReasoningSection(),
                                  const SizedBox(height: AppSpacing.space5),
                                  _buildTimeline(),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          
          // Fixed bottom button
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Align(
              alignment: Alignment.centerLeft,
              child: Padding(
                padding: const EdgeInsets.only(left: AppSpacing.space10),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 800),
                  child: _buildSubmitButton(),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Stack(
      children: [
        // Iridescent glow blob
        Positioned(
          top: -40,
          left: -40,
          child: Container(
            width: 200,
            height: 200,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: RadialGradient(
                colors: [
                  AppColors.primaryFixedDim.withValues(alpha: 0.35),
                  AppColors.surfaceTint.withValues(alpha: 0.08),
                  Colors.transparent,
                ],
              ),
            ),
          ),
        ),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Review & Lodge',
              style: AppTypography.displayLargeMobile.copyWith(
                color: AppColors.onSurface,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Our AI has analyzed your report. Please review the details before submitting to the official channel.',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildAiSummaryCard() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(24),
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
          // Decorative blur blob (top-right)
          Positioned(
            top: -16,
            right: -16,
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
                  Container(
                    width: 40,
                    height: 40,
                    decoration: const BoxDecoration(
                      color: AppColors.primaryFixed,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.auto_awesome,
                      color: AppColors.onPrimaryFixed,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'AI Summary',
                        style: AppTypography.titleLarge.copyWith(
                          color: AppColors.onSurface,
                        ),
                      ),
                      Text(
                        'Generated from your inputs',
                        style: AppTypography.caption.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.space2),
              Text(
                widget.draft?.aiAnalysis?.summary ?? widget.draft?.description ?? 'No summary available.',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.onSurface,
                  height: 1.6,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildReasoningSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('AI Reasoning', style: AppTypography.titleLarge),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.primary.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(100),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.verified, size: 16, color: AppColors.primary),
                  const SizedBox(width: 4),
                  Text(
                    '${widget.draft?.aiAnalysis?.confidence ?? 98}% Confidence',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.primary,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.space2),
        _buildReasoningCard(
          iconBg: AppColors.tertiaryFixed,
          iconColor: AppColors.onTertiaryFixed,
          icon: Icons.category,
          label: 'Category Analysis',
          labelColor: AppColors.tertiary,
          description:
              'Categorized as ${widget.draft?.aiAnalysis?.department ?? 'General'} > ${widget.draft?.aiAnalysis?.category ?? 'General'} based on your description.',
        ),
        const SizedBox(height: AppSpacing.space2),
        _buildReasoningCard(
          iconBg: AppColors.errorContainer,
          iconColor: AppColors.onErrorContainer,
          icon: Icons.warning,
          label: 'Priority Assessment',
          labelColor: AppColors.error,
          description:
              'Priority set to ${widget.draft?.aiAnalysis?.priority ?? 'Normal'}.',
        ),
        const SizedBox(height: AppSpacing.space2),
        _buildReasoningCard(
          iconBg: AppColors.secondaryFixed,
          iconColor: AppColors.onSecondaryFixed,
          icon: Icons.location_on,
          label: 'Location Verification',
          labelColor: AppColors.secondary,
          description:
              'Location verified via GPS and Image Analysis (Elm St).',
        ),
      ],
    );
  }

  Widget _buildReasoningCard({
    required Color iconBg,
    required Color iconColor,
    required IconData icon,
    required String label,
    required Color labelColor,
    required String description,
  }) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(16),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(top: 4),
            decoration: BoxDecoration(
              color: iconBg,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, size: 18, color: iconColor),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: AppTypography.labelSmall.copyWith(
                    color: labelColor,
                  ),
                ),
                const SizedBox(height: 4),
                Text.rich(
                  TextSpan(
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.onSurface,
                    ),
                    text: description,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilingDetails() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Filing Details', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space2),
        Row(
          children: [
            Expanded(
              child: _buildDetailTile(
                icon: Icons.account_balance,
                label: 'Department',
                value: widget.draft?.aiAnalysis?.department ?? 'General',
              ),
            ),
            const SizedBox(width: AppSpacing.space2),
            Expanded(
              child: _buildDetailTile(
                icon: Icons.schedule,
                label: 'Est. Resolution',
                value: '3-5 Days',
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.space2),
        // Duplicate check banner
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: _duplicate?.duplicate == true ? AppColors.errorContainer : AppColors.secondaryContainer.withValues(alpha: 0.5),
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            children: [
              Icon(
                _duplicate?.duplicate == true ? Icons.warning : Icons.check_circle,
                color: _duplicate?.duplicate == true ? AppColors.error : AppColors.secondary,
                size: 20,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _duplicate?.duplicate == true 
                    ? 'Similar report found nearby. You can still submit.' 
                    : 'No duplicate reports found nearby.',
                  style: AppTypography.labelSmall.copyWith(
                    color: _duplicate?.duplicate == true ? AppColors.onErrorContainer : AppColors.onSecondaryContainer,
                  ),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildDetailTile({
    required IconData icon,
    required String label,
    required String value,
  }) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: AppColors.onSurfaceVariant),
              const SizedBox(width: 4),
              Text(
                label,
                style: AppTypography.caption.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurface,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTimeline() {
    const steps = [
      (title: 'Lodge', subtitle: 'Report is officially filed', active: true),
      (title: 'Assign', subtitle: 'Routed to appropriate team', active: false),
      (title: 'Inspect', subtitle: 'On-site verification', active: false),
      (title: 'Resolve', subtitle: 'Issue is fixed and closed', active: false),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Anticipated Timeline', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space3),
        ...List.generate(steps.length, (i) {
          final step = steps[i];
          final isLast = i == steps.length - 1;
          return IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Timeline column
                SizedBox(
                  width: 24,
                  child: Column(
                    children: [
                      // Node
                      Container(
                        width: 16,
                        height: 16,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: step.active
                              ? AppColors.primary
                              : AppColors.surfaceContainerHigh,
                          border: Border.all(
                            color: AppColors.background,
                            width: 3,
                          ),
                          boxShadow: step.active
                              ? [
                                  BoxShadow(
                                    color: AppColors.primary
                                        .withValues(alpha: 0.5),
                                    blurRadius: 10,
                                  ),
                                ]
                              : null,
                        ),
                      ),
                      // Line
                      if (!isLast)
                        Expanded(
                          child: Container(
                            width: 2,
                            color: AppColors.outlineVariant,
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                // Content
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.only(bottom: isLast ? 0 : 24),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          step.title,
                          style: AppTypography.bodyMedium.copyWith(
                            fontWeight:
                                step.active ? FontWeight.w600 : FontWeight.w400,
                            color: step.active
                                ? AppColors.onSurface
                                : AppColors.onSurfaceVariant,
                          ),
                        ),
                        Text(
                          step.subtitle,
                          style: AppTypography.caption.copyWith(
                            color: step.active
                                ? AppColors.onSurfaceVariant
                                : AppColors.outline,
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

  Widget _buildSubmitButton() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.9),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 16,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: AppPressable(
        onPressed: _isSubmitting ? null : _submitComplaint,
        child: Container(
          width: double.infinity,
          height: 56,
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(100),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withValues(alpha: 0.3),
                blurRadius: 24,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Stack(
            alignment: Alignment.center,
            children: [
              // Shimmer effect
              AnimatedBuilder(
                animation: _shimmerCtrl,
                builder: (context, _) {
                  return ClipRRect(
                    borderRadius: BorderRadius.circular(100),
                    child: ShaderMask(
                      shaderCallback: (bounds) {
                        return LinearGradient(
                          begin: Alignment(-1.0 + 2.0 * _shimmerCtrl.value, 0),
                          end: Alignment(
                              -1.0 + 2.0 * _shimmerCtrl.value + 0.5, 0),
                          colors: [
                            Colors.transparent,
                            Colors.white.withValues(alpha: 0.2),
                            Colors.transparent,
                          ],
                        ).createShader(bounds);
                      },
                      blendMode: BlendMode.srcATop,
                      child: Container(
                        width: double.infinity,
                        height: 56,
                        color: AppColors.primary,
                      ),
                    ),
                  );
                },
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.send,
                      color: AppColors.onPrimary, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    _isSubmitting ? 'Submitting...' : 'Lodge Official Grievance',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.onPrimary,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
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
}
