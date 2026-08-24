import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/widgets/core_button.dart';
import '../../core/services/auth_service.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/core_text_field.dart';
import '../../core/widgets/shimmer_loading.dart';
import '../../core/widgets/staggered_entrance.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final AuthService _authService = AuthService();

  final _nameCtrl = TextEditingController();
  final _emailCtrl = TextEditingController();

  ProfileResponse? _profile;
  bool _isLoading = true;
  String? _error;
  bool _isSaving = false;
  String _selectedLanguage = 'ENGLISH';

  static const _languages = [
    'ENGLISH', 'HINDI', 'BENGALI', 'TELUGU', 'MARATHI', 'TAMIL',
    'URDU', 'GUJARATI', 'KANNADA', 'ODIA', 'MALAYALAM', 'PUNJABI',
  ];

  @override
  void initState() {
    super.initState();
    _loadProfile();
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadProfile() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final profile = await _authService.getProfile();
      if (mounted) {
        setState(() {
          _profile = profile;
          _isLoading = false;
          _nameCtrl.text = profile.name;
          _emailCtrl.text = profile.email ?? '';
          _selectedLanguage = _languages.contains(profile.language) ? profile.language : 'ENGLISH';
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = 'Failed to load profile. Please try again.';
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _saveProfile() async {
    if (_profile == null) return;

    setState(() => _isSaving = true);
    try {
      final updated = ProfileResponse(
        id: _profile!.id,
        name: _nameCtrl.text.trim(),
        mobile: _profile!.mobile,
        email: _emailCtrl.text.trim().isNotEmpty ? _emailCtrl.text.trim() : null,
        role: _profile!.role,
        language: _selectedLanguage,
      );
      final result = await _authService.updateProfile(updated);
      if (mounted) {
        setState(() {
          _profile = result;
          _isSaving = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Profile updated.', style: AppTypography.caption.copyWith(color: AppColors.onPrimary)),
            backgroundColor: AppColors.primary,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to update profile.', style: AppTypography.caption.copyWith(color: AppColors.onError)),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  Future<void> _handleLogout() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Log Out'),
        content: const Text('Are you sure you want to log out?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Log Out'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      await _authService.logout();
      if (mounted) context.go('/login');
    }
  }

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/profile', // Profile is accessible from sidebar
        child: _buildDesktop(context),
      ),
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: _buildAppBar(),
      body: _buildBody(),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: _buildAppBar(isDesktop: true),
      body: Center(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxWidth: ResponsiveLayout.contentMaxWidth(context)),
          child: Padding(
            padding: EdgeInsets.symmetric(
              horizontal: ResponsiveLayout.horizontalPadding(context),
            ),
            child: _buildBody(isDesktop: true),
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
        'Profile',
        style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
      ),
    );
  }

  Widget _buildBody({bool isDesktop = false}) {
    if (_isLoading) {
      return ShimmerLoading(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.space3),
          child: Column(
            children: [
              const SizedBox(height: 40),
              Container(width: 80, height: 80, decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle)),
              const SizedBox(height: 24),
              Container(width: 200, height: 20, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8))),
              const SizedBox(height: 32),
              Container(width: double.infinity, height: 56, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12))),
              const SizedBox(height: 16),
              Container(width: double.infinity, height: 56, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(12))),
            ],
          ),
        ),
      );
    }

    if (_error != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48, color: AppColors.error),
            const SizedBox(height: AppSpacing.space2),
            Text(_error!, style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant)),
            const SizedBox(height: AppSpacing.space3),
            TextButton(onPressed: _loadProfile, child: const Text('Retry')),
          ],
        ),
      );
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space3, vertical: AppSpacing.space4),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 480),
          child: StaggeredEntrance(
            children: [
              // Avatar
              Center(
                child: Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withValues(alpha: 0.2),
                        blurRadius: 20,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: Center(
                    child: Text(
                      _profile!.name.isNotEmpty ? _profile!.name[0].toUpperCase() : 'C',
                      style: AppTypography.displayLargeMobile.copyWith(
                        color: AppColors.onPrimaryContainer,
                        fontSize: 32,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.space1),
              Center(
                child: Text(
                  _profile!.mobile,
                  style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
                ),
              ),
              Center(
                child: Container(
                  margin: const EdgeInsets.only(top: 8),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.primaryContainer,
                    borderRadius: BorderRadius.circular(100),
                  ),
                  child: Text(
                    _profile!.role,
                    style: AppTypography.labelSmall.copyWith(color: AppColors.onPrimaryContainer),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.space5),

              // Form
              CoreTextField(
                controller: _nameCtrl,
                label: 'Full Name',
                prefixIcon: Icons.person,
              ),
              const SizedBox(height: AppSpacing.space3),
              CoreTextField(
                controller: _emailCtrl,
                label: 'Email',
                prefixIcon: Icons.email,
              ),
              const SizedBox(height: AppSpacing.space3),

              // Language dropdown
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Preferred Language',
                    style: AppTypography.labelSmall.copyWith(color: AppColors.onSurfaceVariant),
                  ),
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceContainer,
                      borderRadius: BorderRadius.circular(12),
                      border: Border.all(color: AppColors.outlineVariant),
                    ),
                    child: DropdownButtonHideUnderline(
                      child: DropdownButton<String>(
                        value: _selectedLanguage,
                        isExpanded: true,
                        dropdownColor: AppColors.surface,
                        style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
                        items: _languages.map((lang) {
                          return DropdownMenuItem(
                            value: lang,
                            child: Text(lang[0] + lang.substring(1).toLowerCase()),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) setState(() => _selectedLanguage = val);
                        },
                      ),
                    ),
                  ),
                ],
              ),

              const SizedBox(height: AppSpacing.space5),

              CoreButton(
                label: 'Save Changes',
                variant: CoreButtonVariant.primary,
                isLoading: _isSaving,
                icon: Icons.check,
                onPressed: _saveProfile,
              ),

              const SizedBox(height: AppSpacing.space3),

              CoreButton(
                label: 'Log Out',
                variant: CoreButtonVariant.danger,
                icon: Icons.logout,
                onPressed: _handleLogout,
              ),

              const SizedBox(height: AppSpacing.space5),
            ],
          ),
        ),
      ),
    );
  }
}
