import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/models/complaint.dart';
import '../../core/services/auth_service.dart';
import '../../core/services/complaint_service.dart';
import '../../core/services/location_service.dart';
import '../../core/services/notification_service.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/app_pressable.dart';
import '../../core/widgets/staggered_entrance.dart';

/// Civic Companion Home Screen — rebuilt to match Stitch "Civic Companion | Home" design.
/// Features: ambient shader header, AI prompt card, bento grid actions,
/// area map, nearby alerts, info carousel, bottom nav.
class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  String _userName = 'Citizen';

  // Location state
  final LocationService _locationService = LocationService();
  double? _lat;
  double? _lng;
  String _locationLabel = 'Locating...';
  bool _locationLoading = false;
  GoogleMapController? _mapController;

  // Notifications (Nearby Alerts section)
  List<NotificationResponse> _notifications = [];

  // Complaint stats (dashboard) + global search
  final ComplaintService _complaintService = ComplaintService();
  final TextEditingController _searchCtrl = TextEditingController();
  int? _totalComplaints;
  int? _assignedComplaints;
  int? _resolvedComplaints;

  @override
  void initState() {
    super.initState();
    _loadUserName();
    _loadLocation();
    _loadNotifications();
    _loadStats();
  }

  /// Loads the citizen's complaints and computes dashboard stats client-side.
  Future<void> _loadStats() async {
    try {
      final List<ComplaintResponse> complaints =
          await _complaintService.getMyComplaints();
      if (!mounted) return;
      int assigned = 0, resolved = 0;
      for (final c in complaints) {
        final s = c.status.toUpperCase();
        if (s == 'ASSIGNED' || s == 'IN_PROGRESS') assigned++;
        if (s == 'RESOLVED' || s == 'CLOSED') resolved++;
      }
      setState(() {
        _totalComplaints = complaints.length;
        _assignedComplaints = assigned;
        _resolvedComplaints = resolved;
      });
    } catch (_) {
      // Silent fail — stats stay hidden if backend unavailable
    }
  }

  /// Global search — jumps to My Complaints filtered by the query.
  void _onGlobalSearch() {
    final q = _searchCtrl.text.trim();
    if (q.isEmpty) {
      context.go('/my-complaints');
    } else {
      context.go('/my-complaints?q=${Uri.encodeQueryComponent(q)}');
    }
  }

  Future<void> _loadUserName() async {
    final name = await AuthService.getUserName();
    if (mounted) {
      setState(() => _userName = name.isNotEmpty ? name.split(' ')[0] : 'Citizen');
    }
  }

  Future<void> _loadLocation() async {
    if (_locationLoading) return;
    setState(() {
      _locationLoading = true;
      _locationLabel = 'Locating...';
    });
    try {
      final pos = await _locationService.getCurrentLocation();
      if (pos != null && mounted) {
        final address = await _locationService.getAddressFromCoordinates(pos.latitude, pos.longitude);
        setState(() {
          _lat = pos.latitude;
          _lng = pos.longitude;
          _locationLabel = address ?? '${pos.latitude.toStringAsFixed(4)}, ${pos.longitude.toStringAsFixed(4)}';
        });
        _mapController?.animateCamera(
          CameraUpdate.newLatLng(LatLng(pos.latitude, pos.longitude)),
        );
      } else if (mounted) {
        setState(() => _locationLabel = 'Location unavailable');
      }
    } catch (_) {
      if (mounted) setState(() => _locationLabel = 'Location unavailable');
    } finally {
      if (mounted) setState(() => _locationLoading = false);
    }
  }

  Future<void> _loadNotifications() async {
    try {
      final list = await NotificationService().getAllNotifications();
      if (mounted) setState(() => _notifications = list.take(3).toList());
    } catch (_) {
      // Silent fail — no alerts shown if backend unavailable
    }
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/home',
        child: _buildDesktop(context),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Column(
        children: [
          // Fixed Header
          _buildAppBar(),
          // Scrollable body — sections enter with a staggered fade+slide.
          Expanded(
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
                        StaggeredItem(index: 0, child: _buildGreetingHeader()),
                        StaggeredItem(index: 1, child: _buildAiPromptCard()),
                        const SizedBox(height: AppSpacing.space5),
                        StaggeredItem(index: 2, child: _buildStatsSection()),
                        const SizedBox(height: AppSpacing.space5),
                        StaggeredItem(index: 3, child: _buildReportSection()),
                        const SizedBox(height: AppSpacing.space5),
                        StaggeredItem(index: 4, child: _buildAreaSection()),
                        const SizedBox(height: AppSpacing.space5),
                        StaggeredItem(index: 5, child: _buildInfoCarousel()),
                        const SizedBox(height: 110), // floating nav bar space
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/ai-assistant'),
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.psychology, color: AppColors.onPrimary),
        label: Text(
          'Ask AI',
          style: AppTypography.labelSmall.copyWith(color: AppColors.onPrimary),
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Column(
        children: [
          // Fixed Header
          _buildAppBar(),
          // Scrollable body — sections enter with a staggered fade+slide.
          Expanded(
            child: SingleChildScrollView(
              child: Center(
                child: ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: ResponsiveLayout.contentMaxWidth(context)),
                  child: Padding(
                    padding: EdgeInsets.symmetric(
                      horizontal: ResponsiveLayout.horizontalPadding(context),
                      vertical: AppSpacing.space4,
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        StaggeredItem(
                          index: 0,
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Expanded(child: _buildGreetingHeader()),
                              const SizedBox(width: AppSpacing.space4),
                              Expanded(child: _buildAiPromptCard()),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space6),
                        StaggeredItem(index: 1, child: _buildStatsSection()),
                        const SizedBox(height: AppSpacing.space6),
                        StaggeredItem(
                          index: 2,
                          child: _buildReportSection(isDesktop: true),
                        ),
                        const SizedBox(height: AppSpacing.space6),
                        StaggeredItem(
                          index: 3,
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Expanded(child: _buildAreaSection(isDesktop: true)),
                              const SizedBox(width: AppSpacing.space6),
                              Expanded(child: _buildDesktopInfoSection()),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space8),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => context.push('/ai-assistant'),
        backgroundColor: AppColors.primary,
        icon: const Icon(Icons.psychology, color: AppColors.onPrimary),
        label: Text(
          'Ask AI',
          style: AppTypography.labelSmall.copyWith(color: AppColors.onPrimary),
        ),
      ),
    );
  }

  Widget _buildDesktopInfoSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Recent Alerts', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space2),
        if (_notifications.isEmpty)
          Container(
            padding: const EdgeInsets.all(AppSpacing.space3),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(Icons.notifications_none,
                    color: AppColors.onSurfaceVariant.withValues(alpha: 0.5), size: 24),
                const SizedBox(width: AppSpacing.space2),
                Text('No recent alerts',
                    style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
              ],
            ),
          )
        else
          Column(
            children: List.generate(_notifications.length, (i) {
              final n = _notifications[i];
              return Padding(
                padding: EdgeInsets.only(bottom: i < _notifications.length - 1 ? AppSpacing.space2 : 0),
                child: _buildDesktopInfoCard(
                  iconBg: n.read == true
                      ? AppColors.surfaceContainerHigh
                      : AppColors.errorContainer,
                  iconColor: n.read == true
                      ? AppColors.onSurfaceVariant
                      : AppColors.onErrorContainer,
                  icon: n.read == true ? Icons.notifications : Icons.notifications_active,
                  title: n.title ?? 'Notification',
                  subtitle: n.message ?? '',
                ),
              );
            }),
          ),
      ],
    );
  }

  Widget _buildDesktopInfoCard({
    required Color iconBg,
    required Color iconColor,
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.space3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLow,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: iconBg,
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: iconColor, size: 20),
          ),
          const SizedBox(width: AppSpacing.space3),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.w600),
                ),
                Text(
                  subtitle,
                  style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAppBar() {
    return Container(
      color: AppColors.surface.withValues(alpha: 0.92),
      child: SafeArea(
        bottom: false,
        child: Container(
          height: 64,
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space2),
          decoration: BoxDecoration(
            color: AppColors.surface.withValues(alpha: 0.8),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.04),
                blurRadius: 8,
                offset: const Offset(0, 1),
              ),
            ],
          ),
          child: Row(
            children: [
              // Logo
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Icon(
                  Icons.account_balance,
                  color: AppColors.onPrimaryContainer,
                  size: 20,
                ),
              ),
              const SizedBox(width: AppSpacing.space1),
              Text(
                'Dashboard',
                style: AppTypography.titleLarge.copyWith(
                  color: AppColors.onSurface,
                  letterSpacing: -0.4,
                ),
              ),
              const Spacer(),
              // Avatar removed as we use the bottom navbar/sidebar for profile
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildGreetingHeader() {
    return Container(
      margin: const EdgeInsets.only(top: AppSpacing.space3, bottom: AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        children: [
          // Animated gradient shader background
          Positioned.fill(child: _ShaderBackground()),
          // Text overlay
          Padding(
            padding: const EdgeInsets.all(AppSpacing.space3),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Good morning, $_userName.',
                  style: AppTypography.displayLargeMobile.copyWith(
                    color: AppColors.onSurface,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Your city is listening.',
                  style: AppTypography.titleLarge.copyWith(
                    color: AppColors.onSurfaceVariant,
                    fontWeight: FontWeight.w400,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAiPromptCard() {
    return Container(
      margin: const EdgeInsets.only(top: 4),
      padding: const EdgeInsets.all(AppSpacing.space3),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerHighest.withValues(alpha: 0.9),
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.manage_search, color: AppColors.primary, size: 28),
              const SizedBox(width: AppSpacing.space2),
              Text(
                'Search your complaints',
                style: AppTypography.headlineMedium.copyWith(
                  fontSize: 20,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.space3),
          // Global search input — searches across the citizen's complaints
          Focus(
            child: Builder(builder: (context) {
              final focused = Focus.of(context).hasFocus;
              return AnimatedContainer(
                duration: AppAnimations.buttonPress,
                height: 56,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(100),
                  border: Border.all(
                    color: focused ? AppColors.primary : Colors.transparent,
                    width: 2,
                  ),
                  boxShadow: [
                    if (!focused)
                      BoxShadow(
                        color: Colors.black.withValues(alpha: 0.05),
                        blurRadius: 4,
                        offset: const Offset(0, 1),
                      ),
                  ],
                ),
            child: Row(
              children: [
                const SizedBox(width: 16),
                const Icon(Icons.search, color: AppColors.outlineVariant),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: _searchCtrl,
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.onSurface,
                    ),
                    decoration: InputDecoration(
                      hintText: 'Search by title, number, category, status...',
                      hintStyle: AppTypography.bodyMedium.copyWith(
                        color: AppColors.outline,
                      ),
                      border: InputBorder.none,
                    ),
                    textInputAction: TextInputAction.search,
                    onSubmitted: (_) => _onGlobalSearch(),
                  ),
                ),
                AppPressable(
                  onPressed: _onGlobalSearch,
                  child: Container(
                    width: 40,
                    height: 40,
                    margin: const EdgeInsets.only(right: 8),
                    decoration: BoxDecoration(
                      color: AppColors.primaryContainer,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.1),
                          blurRadius: 4,
                        ),
                      ],
                    ),
                    child: const Icon(
                      Icons.arrow_forward,
                      color: AppColors.onPrimaryContainer,
                      size: 20,
                    ),
                  ),
                ),
              ],
            ),
          );
            }),
          ),
        ],
      ),
    );
  }

  /// Dashboard stats — Total / Assigned / Resolved complaint counts.
  Widget _buildStatsSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: AppSpacing.space2),
          child: Text('Your Complaints', style: AppTypography.titleLarge),
        ),
        Row(
          children: [
            Expanded(
              child: _buildStatCard(
                label: 'Total',
                value: _totalComplaints,
                icon: Icons.inbox,
                bg: AppColors.surfaceContainerHigh,
                fg: AppColors.onSurface,
                onTap: () => context.go('/my-complaints'),
              ),
            ),
            const SizedBox(width: AppSpacing.space2),
            Expanded(
              child: _buildStatCard(
                label: 'Assigned',
                value: _assignedComplaints,
                icon: Icons.assignment_ind,
                bg: AppColors.secondaryContainer,
                fg: AppColors.onSecondaryContainer,
                onTap: () => context.go('/my-complaints?status=ASSIGNED'),
              ),
            ),
            const SizedBox(width: AppSpacing.space2),
            Expanded(
              child: _buildStatCard(
                label: 'Resolved',
                value: _resolvedComplaints,
                icon: Icons.check_circle,
                bg: AppColors.primaryContainer,
                fg: AppColors.onPrimaryContainer,
                onTap: () => context.go('/my-complaints?status=RESOLVED'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildStatCard({
    required String label,
    required int? value,
    required IconData icon,
    required Color bg,
    required Color fg,
    required VoidCallback onTap,
  }) {
    return AppPressable(
      onPressed: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.space2),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.06),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: fg, size: 22),
            const SizedBox(height: AppSpacing.space1),
            // Count-up animation once the value loads in.
            TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: (value ?? 0).toDouble()),
              duration: AppAnimations.counterAnim,
              curve: AppAnimations.counterCurve,
              builder: (context, v, child) {
                return Text(
                  value == null ? '—' : '${v.round()}',
                  style: AppTypography.headlineMedium.copyWith(
                    color: fg,
                    fontWeight: FontWeight.w700,
                  ),
                );
              },
            ),
            Text(
              label,
              style: AppTypography.caption.copyWith(
                color: fg.withValues(alpha: 0.8),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildReportSection({bool isDesktop = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: AppSpacing.space2),
          child: Text('Report an Issue', style: AppTypography.titleLarge),
        ),
        _buildCreateComplaintCard(),
      ],
    );
  }

  /// Single unified entry point — opens the Create Complaint screen which
  /// supports typing, voice-to-text, auto location and media attachments.
  Widget _buildCreateComplaintCard() {
    return AppPressable(
      onPressed: () => context.push('/create-complaint'),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.space3),
        decoration: BoxDecoration(
          color: AppColors.primaryContainer,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.06),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.edit_note,
                    color: AppColors.onPrimaryContainer,
                    size: 32,
                  ),
                  const SizedBox(height: AppSpacing.space1),
                  Text(
                    'Create Complaint',
                    style: AppTypography.titleLarge.copyWith(
                      color: AppColors.onPrimaryContainer,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Text(
                    'Type, speak or attach photos',
                    style: AppTypography.caption.copyWith(
                      color: AppColors.onPrimaryContainer.withValues(alpha: 0.8),
                    ),
                  ),
                ],
              ),
            ),
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppColors.onPrimaryContainer.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.chevron_right,
                color: AppColors.onPrimaryContainer,
                size: 24,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAreaSection({bool isDesktop = false}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Your Area
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Your Area', style: AppTypography.titleLarge),
                TextButton.icon(
                  onPressed: _locationLoading ? null : _loadLocation,
                  icon: _locationLoading
                      ? const SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.my_location, size: 16, color: AppColors.primary),
                  label: Text(
                    _locationLoading ? 'Updating...' : 'Update',
                    style: AppTypography.labelSmall.copyWith(color: AppColors.primary),
                  ),
                  style: TextButton.styleFrom(padding: EdgeInsets.zero),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.space2),
            ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: SizedBox(
                height: 240,
                child: _lat != null && _lng != null
                    ? Stack(
                        children: [
                          GoogleMap(
                            initialCameraPosition: CameraPosition(
                              target: LatLng(_lat!, _lng!),
                              zoom: 15,
                            ),
                            onMapCreated: (controller) => _mapController = controller,
                            markers: {
                              Marker(
                                markerId: const MarkerId('citizen'),
                                position: LatLng(_lat!, _lng!),
                                infoWindow: InfoWindow(title: _locationLabel),
                              ),
                            },
                            zoomControlsEnabled: false,
                            myLocationButtonEnabled: false,
                            mapToolbarEnabled: false,
                            liteModeEnabled: true,
                          ),
                          // Location chip overlay
                          Positioned(
                            bottom: 12,
                            left: 12,
                            child: Container(
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                              decoration: BoxDecoration(
                                color: AppColors.surface.withValues(alpha: 0.95),
                                borderRadius: BorderRadius.circular(100),
                                boxShadow: [
                                  BoxShadow(
                                    color: Colors.black.withValues(alpha: 0.12),
                                    blurRadius: 6,
                                  ),
                                ],
                              ),
                              child: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  const Icon(Icons.location_on, color: AppColors.primary, size: 16),
                                  const SizedBox(width: 4),
                                  ConstrainedBox(
                                    constraints: const BoxConstraints(maxWidth: 200),
                                    child: Text(
                                      _locationLabel,
                                      style: AppTypography.caption.copyWith(color: AppColors.onSurface),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      )
                    : Container(
                        color: AppColors.surfaceContainerHigh,
                        child: Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Icon(Icons.location_searching,
                                  size: 40,
                                  color: AppColors.onSurfaceVariant.withValues(alpha: 0.5)),
                              const SizedBox(height: 8),
                              Text(
                                _locationLoading ? 'Getting your location...' : 'Location unavailable',
                                style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
                              ),
                            ],
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.space4),
        // Nearby Alerts — sourced from NotificationService
        Text('Recent Alerts', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space2),
        if (_notifications.isEmpty)
          Container(
            padding: const EdgeInsets.all(AppSpacing.space3),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(Icons.notifications_none,
                    color: AppColors.onSurfaceVariant.withValues(alpha: 0.5), size: 24),
                const SizedBox(width: AppSpacing.space2),
                Text('No recent alerts',
                    style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
              ],
            ),
          )
        else
          ...List.generate(_notifications.length, (i) {
            final n = _notifications[i];
            return Padding(
              padding: EdgeInsets.only(bottom: i < _notifications.length - 1 ? AppSpacing.space1 : 0),
              child: _buildAlertCard(
                icon: n.read == true ? Icons.notifications : Icons.notifications_active,
                iconColor: n.read == true ? AppColors.onSurfaceVariant : AppColors.onErrorContainer,
                bgColor: n.read == true ? AppColors.surfaceContainerHigh : AppColors.errorContainer,
                title: n.title ?? 'Notification',
                subtitle: n.message ?? '',
                filled: n.read != true,
              ),
            );
          }),
      ],
    );
  }

  Widget _buildAlertCard({
    required IconData icon,
    required Color iconColor,
    required Color bgColor,
    required String title,
    required String subtitle,
    required bool filled,
  }) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: iconColor, size: 24),
          const SizedBox(width: AppSpacing.space2),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTypography.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: filled ? AppColors.onErrorContainer : AppColors.onSurface,
                  ),
                ),
                Text(
                  subtitle,
                  style: AppTypography.caption.copyWith(
                    color: filled
                        ? AppColors.onErrorContainer.withValues(alpha: 0.9)
                        : AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoCarousel() {
    // Show quick action links instead of static mock data
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Quick Actions', style: AppTypography.titleLarge),
        const SizedBox(height: AppSpacing.space2),
        Row(
          children: [
            Expanded(
              child: _buildQuickActionCard(
                icon: Icons.history,
                iconBg: AppColors.secondaryContainer,
                iconColor: AppColors.onSecondaryContainer,
                title: 'My Complaints',
                onTap: () => context.go('/my-complaints'),
              ),
            ),
            const SizedBox(width: AppSpacing.space2),
            Expanded(
              child: _buildQuickActionCard(
                icon: Icons.notifications_outlined,
                iconBg: AppColors.primaryContainer,
                iconColor: AppColors.onPrimaryContainer,
                title: 'Alerts',
                onTap: () => context.go('/alerts'),
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildQuickActionCard({
    required IconData icon,
    required Color iconBg,
    required Color iconColor,
    required String title,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.space3),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.04),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(color: iconBg, shape: BoxShape.circle),
              child: Icon(icon, color: iconColor, size: 20),
            ),
            const SizedBox(width: AppSpacing.space2),
            Text(title,
                style: AppTypography.bodyMedium.copyWith(fontWeight: FontWeight.w600)),
          ],
        ),
      ),
    );
  }

}

/// Animated shader-like gradient background using pure Flutter
class _ShaderBackground extends StatefulWidget {
  @override
  State<_ShaderBackground> createState() => _ShaderBackgroundState();
}

class _ShaderBackgroundState extends State<_ShaderBackground>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 5),
    )..repeat();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctrl,
      builder: (context, _) {
        final t = _ctrl.value;
        return CustomPaint(
          painter: _GradientShaderPainter(t),
        );
      },
    );
  }
}

class _GradientShaderPainter extends CustomPainter {
  final double t;
  _GradientShaderPainter(this.t);

  @override
  void paint(Canvas canvas, Size size) {
    final angle1 = t * 2 * math.pi;
    final angle2 = t * 2 * math.pi * 0.7;

    final cx1 = size.width * (0.3 + 0.2 * math.sin(angle1));
    final cy1 = size.height * (0.3 + 0.2 * math.cos(angle1));
    final cx2 = size.width * (0.7 + 0.15 * math.cos(angle2));
    final cy2 = size.height * (0.5 + 0.3 * math.sin(angle2));

    final paint1 = Paint()
      ..shader = RadialGradient(
        colors: [
          const Color(0xFF0052FF).withValues(alpha: 0.12),
          Colors.transparent,
        ],
      ).createShader(Rect.fromCircle(
        center: Offset(cx1, cy1),
        radius: size.width * 0.5,
      ));

    final paint2 = Paint()
      ..shader = RadialGradient(
        colors: [
          const Color(0xFFB7C4FF).withValues(alpha: 0.2),
          Colors.transparent,
        ],
      ).createShader(Rect.fromCircle(
        center: Offset(cx2, cy2),
        radius: size.width * 0.4,
      ));

    canvas.drawRect(Offset.zero & size, paint1);
    canvas.drawRect(Offset.zero & size, paint2);
  }

  @override
  bool shouldRepaint(_GradientShaderPainter old) => old.t != t;
}
