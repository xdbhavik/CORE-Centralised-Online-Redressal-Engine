import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:ui' show ImageFilter;

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';

import '../../core/theme/app_typography.dart';
import '../../core/widgets/core_button.dart';
import '../../core/widgets/staggered_entrance.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/theme/app_radius.dart';
import '../../core/theme/app_shadows.dart';

class LanguageSelectionScreen extends StatefulWidget {
  const LanguageSelectionScreen({super.key});

  @override
  State<LanguageSelectionScreen> createState() => _LanguageSelectionScreenState();
}

class _LanguageSelectionScreenState extends State<LanguageSelectionScreen> {
  final List<Map<String, dynamic>> _languages = [
    {
      'code': 'en',
      'name': 'English',
      'native': 'English',
      'subtitle': 'System Default',
      'icon': Icons.hearing
    },
    {
      'code': 'es',
      'name': 'Español',
      'native': 'Español',
      'subtitle': 'Spanish',
      'icon': Icons.record_voice_over
    },
    {
      'code': 'hi',
      'name': 'Hindi',
      'native': 'हिन्दी',
      'subtitle': 'Hindi',
      'icon': null
    },
    {
      'code': 'zh',
      'name': 'Chinese',
      'native': '中文',
      'subtitle': 'Chinese (Simplified)',
      'icon': Icons.visibility
    },
    {
      'code': 'fr',
      'name': 'French',
      'native': 'Français',
      'subtitle': 'French',
      'icon': null
    },
  ];

  String _selectedLanguageCode = 'en';
  bool _isLoading = true;
  String _searchQuery = '';

  @override
  void initState() {
    super.initState();
    _loadSelectedLanguage();
  }

  Future<void> _loadSelectedLanguage() async {
    final prefs = await SharedPreferences.getInstance();
    final savedCode = prefs.getString('preferred_language');
    if (savedCode != null && _languages.any((lang) => lang['code'] == savedCode)) {
      setState(() {
        _selectedLanguageCode = savedCode;
      });
    }
    setState(() {
      _isLoading = false;
    });
  }

  Future<void> _saveLanguageAndContinue() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('preferred_language', _selectedLanguageCode);
    
    if (mounted) {
      context.go('/onboarding');
    }
  }

  @override
  Widget build(BuildContext context) {
    final filteredLanguages = _languages.where((lang) {
      final query = _searchQuery.toLowerCase();
      return (lang['name'] as String).toLowerCase().contains(query) ||
             (lang['native'] as String).toLowerCase().contains(query);
    }).toList();

    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: ResponsiveLayout(
          mobile: _buildMobile(context, filteredLanguages),
          desktop: _buildDesktop(context, filteredLanguages),
        ),
      ),
      // Sticky Footer is handled inside the views
    );
  }

  Widget _buildMobile(BuildContext context, List<Map<String, dynamic>> filteredLanguages) {
    return Column(
      children: [
        _buildHeader(context),
        
        // Search Section
        Container(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space4, vertical: AppSpacing.space4),
          color: AppColors.surface.withValues(alpha: 0.9),
          child: Container(
            height: 56,
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: TextField(
              onChanged: (value) => setState(() => _searchQuery = value),
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'Search languages...',
                hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
                border: InputBorder.none,
                prefixIcon: const Icon(Icons.search, color: AppColors.onSurfaceVariant),
                suffixIcon: const Icon(Icons.mic, color: AppColors.onSurfaceVariant, size: 18),
                contentPadding: const EdgeInsets.symmetric(vertical: 16),
              ),
            ),
          ),
        ),
        
        Expanded(
          child: Stack(
            children: [
              _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : SingleChildScrollView(
                      padding: const EdgeInsets.only(
                        left: AppSpacing.space4, 
                        right: AppSpacing.space4,
                        bottom: 120, // space for sticky footer
                      ),
                      child: _buildLanguageList(filteredLanguages),
                    ),
              Positioned(
                bottom: 0,
                left: 0,
                right: 0,
                child: _buildStickyFooter(),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildDesktop(BuildContext context, List<Map<String, dynamic>> filteredLanguages) {
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1200),
        child: Row(
          children: [
            // ── Left Column (Branding) ──
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
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.space10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
                        onPressed: () {
                          if (GoRouter.of(context).canPop()) {
                            context.pop();
                          } else {
                            context.go('/welcome');
                          }
                        },
                      ),
                      const Spacer(),
                      Container(
                        width: 80,
                        height: 80,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainerHighest,
                          borderRadius: AppRadius.borderRadiusXl,
                          boxShadow: AppShadows.level2,
                        ),
                        child: const Icon(
                          Icons.language,
                          size: 48,
                          color: AppColors.primary,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space6),
                      Text(
                        'Choose Language.',
                        style: Theme.of(context).textTheme.displayLarge?.copyWith(
                          color: AppColors.onSurface,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space3),
                      Text(
                        'Select your preferred language to interact with CORE.',
                        style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                      const Spacer(),
                    ],
                  ),
                ),
              ),
            ),

            // ── Right Column (List) ──
            Expanded(
              flex: 9, // 45%
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space10),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      children: [
                        const SizedBox(height: AppSpacing.space6),
                        // Search Section
                        Container(
                          height: 56,
                          decoration: BoxDecoration(
                            color: AppColors.surfaceContainerLow,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: TextField(
                            onChanged: (value) => setState(() => _searchQuery = value),
                            style: AppTypography.bodyMedium,
                            decoration: InputDecoration(
                              hintText: 'Search languages...',
                              hintStyle: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
                              border: InputBorder.none,
                              prefixIcon: const Icon(Icons.search, color: AppColors.onSurfaceVariant),
                              suffixIcon: const Icon(Icons.mic, color: AppColors.onSurfaceVariant, size: 18),
                              contentPadding: const EdgeInsets.symmetric(vertical: 16),
                            ),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space4),
                        Expanded(
                          child: Stack(
                            children: [
                              _isLoading
                                  ? const Center(child: CircularProgressIndicator())
                                  : SingleChildScrollView(
                                      padding: const EdgeInsets.only(bottom: 120),
                                      child: _buildLanguageList(filteredLanguages),
                                    ),
                              Positioned(
                                bottom: 0,
                                left: 0,
                                right: 0,
                                child: _buildStickyFooter(),
                              ),
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
    );
  }

  Widget _buildLanguageList(List<Map<String, dynamic>> filteredLanguages) {
    return StaggeredEntrance(
                        children: filteredLanguages.map((lang) {
                          final isSelected = _selectedLanguageCode == lang['code'];
                          
                          return Padding(
                            padding: const EdgeInsets.only(bottom: AppSpacing.space3),
                            child: InkWell(
                              onTap: () {
                                setState(() {
                                  _selectedLanguageCode = lang['code'] as String;
                                });
                              },
                              borderRadius: BorderRadius.circular(12),
                              child: AnimatedContainer(
                                duration: const Duration(milliseconds: 200),
                                padding: const EdgeInsets.all(AppSpacing.space4),
                                decoration: BoxDecoration(
                                  color: isSelected ? AppColors.primaryContainer : AppColors.surfaceContainerLow,
                                  borderRadius: BorderRadius.circular(12),
                                  boxShadow: [
                                    BoxShadow(
                                      color: Colors.black.withValues(alpha: 0.02),
                                      blurRadius: 4,
                                      offset: const Offset(0, 2),
                                    )
                                  ],
                                ),
                                child: Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            lang['native'] as String,
                                            style: AppTypography.titleLarge.copyWith(
                                              color: isSelected ? AppColors.onPrimaryContainer : AppColors.onSurface,
                                            ),
                                          ),
                                          const SizedBox(height: AppSpacing.space1),
                                          Text(
                                            lang['subtitle'] as String,
                                            style: AppTypography.labelSmall.copyWith(
                                              color: isSelected ? AppColors.onPrimaryContainer.withValues(alpha: 0.8) : AppColors.onSurfaceVariant,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                    Row(
                                      children: [
                                        if (lang['icon'] != null) ...[
                                          Container(
                                            width: 32,
                                            height: 32,
                                            decoration: BoxDecoration(
                                              color: isSelected ? AppColors.surfaceContainerLowest.withValues(alpha: 0.3) : AppColors.surfaceContainer,
                                              shape: BoxShape.circle,
                                            ),
                                            child: Icon(
                                              lang['icon'] as IconData,
                                              size: 20,
                                              color: isSelected ? AppColors.onPrimaryContainer : AppColors.onSurfaceVariant,
                                            ),
                                          ),
                                          const SizedBox(width: AppSpacing.space3),
                                        ],
                                        Icon(
                                          isSelected ? Icons.check_circle : Icons.radio_button_unchecked,
                                          color: isSelected ? AppColors.primary : AppColors.surfaceContainerLow,
                                        ),
                                      ],
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          );
                        }).toList(),
    );
  }

  Widget _buildStickyFooter() {
    return ClipRRect(
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
        child: Container(
          padding: const EdgeInsets.fromLTRB(AppSpacing.space4, AppSpacing.space4, AppSpacing.space4, AppSpacing.space6),
          decoration: BoxDecoration(
            color: AppColors.surface.withValues(alpha: 0.8),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: 0.06),
                blurRadius: 24,
                offset: const Offset(0, -4),
              )
            ],
          ),
          child: SafeArea(
            top: false,
            child: CoreButton(
              label: 'Confirm Selection',
              icon: Icons.arrow_forward,
              variant: CoreButtonVariant.primary,
              onPressed: _saveLanguageAndContinue,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.8),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 1),
          )
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.onSurfaceVariant),
            onPressed: () {
              if (GoRouter.of(context).canPop()) {
                context.pop();
              } else {
                context.go('/welcome');
              }
            },
          ),
          Text('Language Selection', style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface)),
          Container(
            width: 32,
            height: 32,
            margin: const EdgeInsets.only(right: 8),
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: const Icon(Icons.person, color: AppColors.onPrimary, size: 18),
          ),
        ],
      ),
    );
  }
}
