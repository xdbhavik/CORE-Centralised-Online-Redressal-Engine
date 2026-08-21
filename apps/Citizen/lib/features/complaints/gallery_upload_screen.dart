import 'dart:io';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_radius.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../services/image_service.dart';

/// Gallery Upload Screen — gallery-only image picker.
/// Uses image_picker on Android/iOS, file_picker on Windows.
/// After selection, navigates to AI Review with the image path.
class GalleryUploadScreen extends StatefulWidget {
  const GalleryUploadScreen({super.key});

  @override
  State<GalleryUploadScreen> createState() => _GalleryUploadScreenState();
}

class _GalleryUploadScreenState extends State<GalleryUploadScreen>
    with TickerProviderStateMixin {
  final ImageService _imageService = ImageService();

  File? _selectedImage;
  bool _isProcessing = false;
  String? _errorMessage;

  // Animations
  late AnimationController _previewEntranceCtrl;
  late Animation<double> _previewFade;
  late Animation<double> _previewScale;
  late AnimationController _errorCtrl;

  @override
  void initState() {
    super.initState();
    _previewEntranceCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
    _previewFade = CurvedAnimation(
      parent: _previewEntranceCtrl,
      curve: Curves.easeOutCubic,
    );
    _previewScale = Tween<double>(begin: 0.95, end: 1.0).animate(
      CurvedAnimation(
        parent: _previewEntranceCtrl,
        curve: Curves.easeOutCubic,
      ),
    );
    _errorCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
  }

  @override
  void dispose() {
    _previewEntranceCtrl.dispose();
    _errorCtrl.dispose();
    super.dispose();
  }

  Future<void> _pickFromGallery() async {
    setState(() => _errorMessage = null);
    try {
      final file = await _imageService.pickFromGallery();
      if (file != null) {
        setState(() => _selectedImage = file);
        _previewEntranceCtrl.forward(from: 0);
      }
    } catch (e) {
      setState(() => _errorMessage = e.toString());
      _errorCtrl.forward(from: 0);
    }
  }

  void _removeImage() {
    setState(() {
      _selectedImage = null;
      _errorMessage = null;
    });
  }

  Future<void> _proceed() async {
    if (_selectedImage == null) return;
    setState(() {
      _isProcessing = true;
      _errorMessage = null;
    });

    try {
      final draft = ComplaintDraft(
        description: '',
        imagePath: _selectedImage!.path,
      );

      if (mounted) {
        context.push('/ai-review', extra: draft);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isProcessing = false;
          _errorMessage = 'Something went wrong. Please try again.';
        });
        _errorCtrl.forward(from: 0);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ResponsiveLayout(
      mobile: _buildMobile(context),
      desktop: DesktopSidebar(
        currentRoute: '/new-grievance',
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
          'Upload from Gallery',
          style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
                maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: _buildContent(),
          ),
        ),
      ),
    );
  }

  Widget _buildDesktop(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      body: Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.space10,
          vertical: AppSpacing.space6,
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back,
                      color: AppColors.onSurface),
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
                  'Upload from Gallery',
                  style: AppTypography.headlineMedium
                      .copyWith(color: AppColors.onSurface),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.space6),
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 520),
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: AppRadius.borderRadiusXl,
                      border: Border.all(
                        color:
                            AppColors.outlineVariant.withValues(alpha: 0.5),
                      ),
                    ),
                    child: _buildContent(),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent() {
    return Padding(
      padding: const EdgeInsets.all(AppSpacing.space3),
      child: Column(
        children: [
          Expanded(
            flex: 5,
            child: _buildPreviewArea(),
          ),
          const SizedBox(height: AppSpacing.space3),
          if (_errorMessage != null) _buildErrorMessage(),
          _buildActionButtons(),
        ],
      ),
    );
  }

  Widget _buildPreviewArea() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: double.infinity,
        decoration: BoxDecoration(
          color: AppColors.surfaceContainerLow,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
            color: AppColors.outlineVariant.withValues(alpha: 0.3),
          ),
        ),
        child: _selectedImage != null
            ? _buildImagePreview()
            : _buildEmptyState(),
      ),
    );
  }

  Widget _buildEmptyState() {
    return InkWell(
      onTap: _pickFromGallery,
      borderRadius: BorderRadius.circular(16),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.primaryContainer.withValues(alpha: 0.3),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.photo_library_outlined,
              color: AppColors.primary,
              size: 36,
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Tap to select a photo',
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space1),
          Text(
            'Choose an image from your device',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildImagePreview() {
    return FadeTransition(
      opacity: _previewFade,
      child: ScaleTransition(
        scale: _previewScale,
        child: Stack(
          fit: StackFit.expand,
          children: [
            Image.file(_selectedImage!, fit: BoxFit.cover),
            if (_isProcessing)
              Container(
                color: AppColors.inverseSurface.withValues(alpha: 0.5),
                child: const Center(
                  child: CircularProgressIndicator(
                    color: AppColors.onPrimary,
                    strokeWidth: 3,
                  ),
                ),
              ),
            if (!_isProcessing)
              Positioned(
                top: 12,
                right: 12,
                child: GestureDetector(
                  onTap: _removeImage,
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.inverseSurface.withValues(alpha: 0.7),
                      borderRadius: BorderRadius.circular(100),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.close,
                            size: 16, color: Colors.white),
                        const SizedBox(width: 4),
                        Text(
                          'Remove',
                          style: AppTypography.labelSmall.copyWith(
                            color: Colors.white,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorMessage() {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.space2),
      child: AnimatedBuilder(
        animation: _errorCtrl,
        builder: (context, child) {
          final shakeValue = _errorCtrl.value;
          double dx = 0;
          if (shakeValue < 0.25) {
            dx = 4.0 * (shakeValue / 0.25);
          } else if (shakeValue < 0.5) {
            dx = 4.0 * (1 - (shakeValue - 0.25) / 0.25);
          } else if (shakeValue < 0.75) {
            dx = -4.0 * ((shakeValue - 0.5) / 0.25);
          } else {
            dx = -4.0 * (1 - (shakeValue - 0.75) / 0.25);
          }
          return Transform.translate(
            offset: Offset(dx, 0),
            child: Opacity(
              opacity: _errorCtrl.value.clamp(0.0, 1.0),
              child: child,
            ),
          );
        },
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppColors.errorContainer,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              const Icon(Icons.error_outline,
                  color: AppColors.onErrorContainer, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _errorMessage!,
                  style: AppTypography.caption.copyWith(
                    color: AppColors.onErrorContainer,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActionButtons() {
    return Column(
      children: [
        // Gallery button
        _PressableButton(
          onPressed: _isProcessing ? null : _pickFromGallery,
          child: Container(
            width: double.infinity,
            height: 56,
            decoration: BoxDecoration(
              color: AppColors.secondaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.photo_library,
                    color: AppColors.onSecondaryContainer, size: 20),
                const SizedBox(width: 8),
                Text(
                  _selectedImage != null ? 'Choose Another' : 'Choose from Gallery',
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.onSecondaryContainer,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.space2),

        // Submit button
        _PressableButton(
          onPressed:
              (_selectedImage != null && !_isProcessing) ? _proceed : null,
          child: AnimatedOpacity(
            opacity: _selectedImage != null ? 1.0 : 0.4,
            duration: const Duration(milliseconds: 200),
            child: Container(
              width: double.infinity,
              height: 56,
              decoration: BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.circular(12),
                boxShadow: _selectedImage != null
                    ? [
                        BoxShadow(
                          color: AppColors.primary.withValues(alpha: 0.3),
                          blurRadius: 16,
                          offset: const Offset(0, 4),
                        ),
                      ]
                    : null,
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (_isProcessing)
                    const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        color: AppColors.onPrimary,
                        strokeWidth: 2,
                      ),
                    )
                  else
                    const Icon(Icons.arrow_forward,
                        color: AppColors.onPrimary, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    _isProcessing ? 'Processing...' : 'Proceed',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.onPrimary,
                      fontWeight: FontWeight.w600,
                      fontSize: 14,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _PressableButton extends StatefulWidget {
  final Widget child;
  final VoidCallback? onPressed;

  const _PressableButton({required this.child, this.onPressed});

  @override
  State<_PressableButton> createState() => _PressableButtonState();
}

class _PressableButtonState extends State<_PressableButton>
    with SingleTickerProviderStateMixin {
  late AnimationController _ctrl;
  late Animation<double> _scale;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
      vsync: this,
      duration: AppAnimations.buttonPress,
    );
    _scale = Tween<double>(begin: 1.0, end: 0.97).animate(
      CurvedAnimation(parent: _ctrl, curve: AppAnimations.buttonPressCurve),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: widget.onPressed != null ? (_) => _ctrl.forward() : null,
      onTapUp: widget.onPressed != null
          ? (_) {
              _ctrl.reverse();
              widget.onPressed?.call();
            }
          : null,
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(scale: _scale, child: widget.child),
    );
  }
}
