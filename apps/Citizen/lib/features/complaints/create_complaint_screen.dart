import 'dart:io';

import 'package:dio/dio.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/models/complaint.dart';
import '../../core/services/complaint_service.dart';
import '../../core/services/location_service.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/widgets/complaint_map_widget.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/staggered_entrance.dart';
import 'widgets/voice_input_sheet.dart';

/// Unified "Create Complaint" screen.
///
/// Flow for the citizen (top → bottom):
/// 1. Title (optional)
/// 2. Description — type manually OR fill via speech-to-text (mic button)
/// 3. Location — latitude/longitude auto-filled from GPS. The app clearly
///    informs the citizen that THIS address will be used for the complaint;
///    if reporting from a different place, the address can be edited manually.
/// 4. Media — add photos / videos / documents (at the end)
/// 5. Submit Complaint — creates the complaint on the backend immediately
///    and uploads all attached media.
class CreateComplaintScreen extends StatefulWidget {
  const CreateComplaintScreen({super.key});

  @override
  State<CreateComplaintScreen> createState() => _CreateComplaintScreenState();
}

class _CreateComplaintScreenState extends State<CreateComplaintScreen> {
  // ── Form controllers ──────────────────────────────────────────────────────
  final TextEditingController _titleCtrl = TextEditingController();
  final TextEditingController _descCtrl = TextEditingController();
  final TextEditingController _addressCtrl = TextEditingController();

  // ── Location ──────────────────────────────────────────────────────────────
  final LocationService _locationService = LocationService();
  double? _latitude;
  double? _longitude;
  bool _locationLoading = true;
  String? _locationError;

  // ── Media ─────────────────────────────────────────────────────────────────
  final ImagePicker _imagePicker = ImagePicker();
  final List<String> _mediaPaths = [];
  static const int _maxMedia = 5;

  // ── Submit ────────────────────────────────────────────────────────────────
  final ComplaintService _complaintService = ComplaintService();
  bool _isSubmitting = false;

  static const _imageExts = ['jpg', 'jpeg', 'png'];

  @override
  void initState() {
    super.initState();
    // Rebuild on description change (char counter + submit button state).
    _descCtrl.addListener(() => setState(() {}));
    _autoDetectLocation();
  }

  @override
  void dispose() {
    _titleCtrl.dispose();
    _descCtrl.dispose();
    _addressCtrl.dispose();
    super.dispose();
  }

  // ─── Location: auto-fill from GPS ─────────────────────────────────────────

  Future<void> _autoDetectLocation() async {
    setState(() {
      _locationLoading = true;
      _locationError = null;
    });
    try {
      final position = await _locationService.getCurrentLocation();
      if (position == null) {
        if (mounted) {
          setState(() {
            _locationLoading = false;
            _locationError =
                'Could not detect location automatically. Tap the map to set it manually.';
          });
        }
        return;
      }
      await _applyLocation(position.latitude, position.longitude,
          updateAddress: true);
    } catch (_) {
      if (mounted) {
        setState(() {
          _locationLoading = false;
          _locationError =
              'Could not detect location automatically. Tap the map to set it manually.';
        });
      }
    } finally {
      if (mounted) setState(() => _locationLoading = false);
    }
  }

  /// Sets lat/lng and (optionally) reverse-geocodes the address field.
  Future<void> _applyLocation(double lat, double lng,
      {bool updateAddress = true}) async {
    if (mounted) {
      setState(() {
        _latitude = lat;
        _longitude = lng;
      });
    }
    if (updateAddress) {
      final address = await _locationService.getAddressFromCoordinates(lat, lng);
      if (address != null && mounted) {
        _addressCtrl.text = address;
      }
    }
  }

  // ─── Speech-to-text input ─────────────────────────────────────────────────

  Future<void> _openVoiceInput() async {
    final transcript = await showVoiceInputSheet(context);
    if (transcript != null && transcript.isNotEmpty) {
      final current = _descCtrl.text;
      final separator = current.isNotEmpty && !current.endsWith(' ') ? ' ' : '';
      var combined = '$current$separator$transcript';
      if (combined.length > 500) combined = combined.substring(0, 500);
      _descCtrl.text = combined;
      _descCtrl.selection = TextSelection.fromPosition(
        TextPosition(offset: _descCtrl.text.length),
      );
    }
  }

  // ─── Media picking ────────────────────────────────────────────────────────

  bool _isImagePath(String path) {
    final ext = path.split('.').last.toLowerCase();
    return _imageExts.contains(ext);
  }

  void _addMediaPath(String path) {
    if (_mediaPaths.length >= _maxMedia) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Maximum 5 media files allowed.')),
      );
      return;
    }
    if (!_mediaPaths.contains(path)) {
      setState(() => _mediaPaths.add(path));
    }
  }

  Future<void> _pickFromCamera() async {
    try {
      final photo = await _imagePicker.pickImage(
        source: ImageSource.camera,
        imageQuality: 80,
      );
      if (photo != null) _addMediaPath(photo.path);
    } catch (_) {
      _showMediaError();
    }
  }

  Future<void> _pickFromGallery() async {
    try {
      final photo = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 80,
      );
      if (photo != null) _addMediaPath(photo.path);
    } catch (_) {
      _showMediaError();
    }
  }

  Future<void> _pickFromFiles() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'jpeg', 'png', 'mp4', 'pdf'],
        allowMultiple: true,
      );
      if (result != null) {
        for (final file in result.files) {
          if (file.path != null) _addMediaPath(file.path!);
        }
      }
    } catch (_) {
      _showMediaError();
    }
  }

  void _showMediaError() {
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Failed to add media. Please try again.')),
      );
    }
  }

  void _removeMedia(String path) {
    setState(() => _mediaPaths.remove(path));
  }

  // ─── Submit ───────────────────────────────────────────────────────────────

  bool get _canSubmit =>
      _descCtrl.text.trim().length > 15 &&
      _latitude != null &&
      _longitude != null;

  Future<void> _submitComplaint() async {
    if (!_canSubmit || _isSubmitting) return;
    setState(() => _isSubmitting = true);

    try {
      final userTitle = _titleCtrl.text.trim();
      final userAddress = _addressCtrl.text.trim();

      final response = await _complaintService.createComplaint(
        title: userTitle.isEmpty ? null : userTitle,
        description: _descCtrl.text.trim(),
        latitude: _latitude,
        longitude: _longitude,
        address: userAddress.isEmpty ? null : userAddress,
      );

      // Backend confirmed this as a duplicate of an existing complaint —
      // the returned complaintId belongs to the EXISTING complaint, so skip
      // media upload and inform the citizen.
      if (response.isDuplicate) {
        if (mounted) {
          setState(() => _isSubmitting = false);
          await _showDuplicateDialog(response);
          if (mounted) context.go('/my-complaints');
        }
        return;
      }

      // Upload every attached media file (photos / videos / documents).
      for (final path in _mediaPaths) {
        await _complaintService.uploadMedia(response.complaintId, File(path));
      }

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Complaint ${response.complaintNumber} submitted successfully!',
            ),
          ),
        );
        context.go('/my-complaints');
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _errorMessage(e),
              style: AppTypography.caption.copyWith(color: AppColors.onError),
            ),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  /// Extracts the backend's actual error message (rate limit, validation, etc.)
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
    return 'Failed to submit complaint. Please try again.';
  }

  Future<void> _showDuplicateDialog(ComplaintResponse response) {
    final existingNo =
        response.existingComplaintNumber ?? response.complaintNumber;
    final existingStatus = response.existingStatus;
    final reason = response.reasons.isNotEmpty ? response.reasons.first : null;

    return showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.content_copy,
            color: AppColors.tertiary, size: 32),
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

  // ─── Build ────────────────────────────────────────────────────────────────

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
          'Create Complaint',
          style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(
                maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: Stack(
              children: [
                SingleChildScrollView(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space2,
                  ),
                  child: _buildFormContent(),
                ),
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: _buildBottomBar(),
                ),
              ],
            ),
          ),
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
                      'Create Complaint',
                      style: AppTypography.headlineMedium
                          .copyWith(color: AppColors.onSurface),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.space4),
                Expanded(
                  child: Center(
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 720),
                      child: SingleChildScrollView(
                        child: _buildFormContent(),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Align(
              alignment: Alignment.center,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 720),
                child: _buildBottomBar(),
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Shared form body used by both mobile & desktop layouts.
  /// Sections enter with a staggered fade+slide for a smooth first impression.
  Widget _buildFormContent() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const SizedBox(height: AppSpacing.space3),
        StaggeredItem(
          index: 0,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'What went wrong?',
                style: AppTypography.headlineMedium.copyWith(
                  fontSize: 26,
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                'Fill the details below — type or speak, we will route it correctly.',
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.space4),

        // ── 1. Title (optional) ──────────────────────────────────────────
        StaggeredItem(
          index: 1,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSectionLabel('Title', optional: true),
              const SizedBox(height: 8),
              _buildTitleField(),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.space4),

        // ── 2. Description (type or speak) ───────────────────────────────
        StaggeredItem(
          index: 2,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSectionLabel('Description'),
              const SizedBox(height: 8),
              _buildDescriptionField(),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.space4),

        // ── 3. Location ──────────────────────────────────────────────────
        StaggeredItem(
          index: 3,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSectionLabel('Location'),
              const SizedBox(height: 8),
              _buildLocationInfoBanner(),
              const SizedBox(height: AppSpacing.space2),
              _buildLatLngRow(),
              const SizedBox(height: AppSpacing.space2),
              _buildAddressField(),
              const SizedBox(height: AppSpacing.space2),
              ComplaintMapWidget(
                key: ValueKey('${_latitude ?? 0}_${_longitude ?? 0}'),
                initialLatitude: _latitude,
                initialLongitude: _longitude,
                onLocationSelected: (lat, lng) {
                  _applyLocation(lat, lng, updateAddress: true);
                },
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.space4),

        // ── 4. Media (at the end) ────────────────────────────────────────
        StaggeredItem(
          index: 4,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSectionLabel('Add Media', optional: true),
              const SizedBox(height: 8),
              _buildMediaSection(),
            ],
          ),
        ),
        const SizedBox(height: 96), // room for bottom bar
      ],
    );
  }

  Widget _buildSectionLabel(String text, {bool optional = false}) {
    return Row(
      children: [
        Text(
          text,
          style: AppTypography.titleLarge.copyWith(fontWeight: FontWeight.w600),
        ),
        if (optional) ...[
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerHigh,
              borderRadius: BorderRadius.circular(100),
            ),
            child: Text(
              'Optional',
              style: AppTypography.labelSmall
                  .copyWith(color: AppColors.onSurfaceVariant),
            ),
          ),
        ],
      ],
    );
  }

  // ─── Title field ──────────────────────────────────────────────────────────

  Widget _buildTitleField() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
          ),
        ],
      ),
      child: TextField(
        controller: _titleCtrl,
        maxLength: 100,
        style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
        decoration: InputDecoration(
          hintText: 'e.g., Streetlight not working near market',
          hintStyle:
              AppTypography.bodyMedium.copyWith(color: AppColors.outline),
          counterText: '',
          prefixIcon:
              const Icon(Icons.title, color: AppColors.onSurfaceVariant),
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          border: InputBorder.none,
        ),
      ),
    );
  }

  // ─── Description field (type + mic) ───────────────────────────────────────

  Widget _buildDescriptionField() {
    return Container(
      height: 190,
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
          ),
        ],
      ),
      child: Column(
        children: [
          Expanded(
            child: TextField(
              controller: _descCtrl,
              maxLines: null,
              expands: true,
              maxLength: 500,
              style:
                  AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
              decoration: InputDecoration(
                hintText: 'Type your complaint here, or tap the mic to speak...',
                hintStyle:
                    AppTypography.bodyMedium.copyWith(color: AppColors.outline),
                counterText: '',
                contentPadding: const EdgeInsets.all(16),
                border: InputBorder.none,
              ),
            ),
          ),
          // Toolbar: mic (speech-to-text) + counter
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: const BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius:
                  BorderRadius.vertical(bottom: Radius.circular(12)),
              border: Border(
                top: BorderSide(color: AppColors.surfaceVariant),
              ),
            ),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.mic, size: 22),
                  color: AppColors.primary,
                  tooltip: 'Speak to fill description',
                  onPressed: _openVoiceInput,
                  padding: EdgeInsets.zero,
                  constraints:
                      const BoxConstraints(minWidth: 40, minHeight: 40),
                ),
                Text(
                  'Voice to text',
                  style: AppTypography.caption
                      .copyWith(color: AppColors.onSurfaceVariant),
                ),
                const Spacer(),
                Text(
                  '${_descCtrl.text.length} / 500',
                  style:
                      AppTypography.caption.copyWith(color: AppColors.outline),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ─── Location widgets ─────────────────────────────────────────────────────

  Widget _buildLocationInfoBanner() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.primaryContainer.withValues(alpha: 0.35),
        borderRadius: BorderRadius.circular(12),
        border:
            Border.all(color: AppColors.primary.withValues(alpha: 0.15)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.info_outline, size: 18, color: AppColors.primary),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              'Your current location has been filled automatically and this address will be used for your complaint. '
              'If you are reporting an issue at a different place, edit the address below manually.',
              style: AppTypography.caption.copyWith(
                color: AppColors.onSurface,
                height: 1.4,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildLatLngRow() {
    if (_locationLoading) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'Detecting your location...',
              style: AppTypography.caption
                  .copyWith(color: AppColors.onSurfaceVariant),
            ),
          ],
        ),
      );
    }

    if (_locationError != null && _latitude == null) {
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: AppColors.errorContainer.withValues(alpha: 0.5),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            const Icon(Icons.location_off, size: 16, color: AppColors.error),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                _locationError!,
                style: AppTypography.caption
                    .copyWith(color: AppColors.onErrorContainer),
              ),
            ),
          ],
        ),
      );
    }

    return Row(
      children: [
        Expanded(
          child: _buildCoordChip(
            label: 'Latitude',
            value: _latitude?.toStringAsFixed(6) ?? '—',
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: _buildCoordChip(
            label: 'Longitude',
            value: _longitude?.toStringAsFixed(6) ?? '—',
          ),
        ),
        const SizedBox(width: 8),
        // Re-detect button
        InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: _autoDetectLocation,
          child: Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.primaryContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(Icons.my_location,
                size: 20, color: AppColors.onPrimaryContainer),
          ),
        ),
      ],
    );
  }

  Widget _buildCoordChip({required String label, required String value}) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainer,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: AppTypography.labelSmall
                .copyWith(color: AppColors.onSurfaceVariant),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurface,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAddressField() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 6,
          ),
        ],
      ),
      child: TextField(
        controller: _addressCtrl,
        maxLines: 2,
        minLines: 1,
        style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurface),
        decoration: InputDecoration(
          hintText: 'Complaint address (edit if reporting for another place)',
          hintStyle:
              AppTypography.bodyMedium.copyWith(color: AppColors.outline),
          prefixIcon: const Icon(Icons.location_on_outlined,
              color: AppColors.onSurfaceVariant),
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          border: InputBorder.none,
        ),
      ),
    );
  }

  // ─── Media section ────────────────────────────────────────────────────────

  Widget _buildMediaSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Picker buttons
        Row(
          children: [
            Expanded(
              child: _buildMediaButton(
                icon: Icons.photo_camera,
                label: 'Camera',
                onTap: _pickFromCamera,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildMediaButton(
                icon: Icons.photo_library,
                label: 'Gallery',
                onTap: _pickFromGallery,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _buildMediaButton(
                icon: Icons.attach_file,
                label: 'File',
                onTap: _pickFromFiles,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Text(
          'Photos, videos (mp4) or documents (pdf) — up to $_maxMedia files.',
          style:
              AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
        ),
        // Selected media thumbnails — each pops in with a scale+fade.
        if (_mediaPaths.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.space2),
          SizedBox(
            height: 84,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _mediaPaths.length,
              separatorBuilder: (context, index) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final path = _mediaPaths[index];
                return TweenAnimationBuilder<double>(
                  key: ValueKey(path),
                  tween: Tween(begin: 0, end: 1),
                  duration: AppAnimations.cardEntrance,
                  curve: AppAnimations.cardEntranceCurve,
                  builder: (context, v, child) => Opacity(
                    opacity: v,
                    child: Transform.scale(
                      scale: 0.8 + 0.2 * v,
                      child: child,
                    ),
                  ),
                  child: _buildMediaThumb(path),
                );
              },
            ),
          ),
        ],
      ],
    );
  }

  Widget _buildMediaButton({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      borderRadius: BorderRadius.circular(12),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppColors.outlineVariant),
        ),
        child: Column(
          children: [
            Icon(icon, color: AppColors.primary, size: 24),
            const SizedBox(height: 4),
            Text(
              label,
              style: AppTypography.labelSmall
                  .copyWith(color: AppColors.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMediaThumb(String path) {
    final isImage = _isImagePath(path);
    final ext = path.split('.').last.toLowerCase();
    final fileName = path.split(Platform.pathSeparator).last;

    return Stack(
      children: [
        Container(
          width: 84,
          height: 84,
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.outlineVariant),
          ),
          clipBehavior: Clip.antiAlias,
          child: isImage
              ? Image.file(
                  File(path),
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) => const Icon(
                    Icons.broken_image,
                    color: AppColors.outlineVariant,
                  ),
                )
              : Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      ext == 'mp4' ? Icons.videocam : Icons.picture_as_pdf,
                      color: AppColors.primary,
                      size: 26,
                    ),
                    const SizedBox(height: 4),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Text(
                        fileName,
                        style: AppTypography.labelSmall.copyWith(
                            color: AppColors.onSurfaceVariant, fontSize: 9),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        textAlign: TextAlign.center,
                      ),
                    ),
                  ],
                ),
        ),
        Positioned(
          top: 2,
          right: 2,
          child: GestureDetector(
            onTap: () => _removeMedia(path),
            child: Container(
              padding: const EdgeInsets.all(2),
              decoration: const BoxDecoration(
                color: AppColors.error,
                shape: BoxShape.circle,
              ),
              child:
                  const Icon(Icons.close, size: 14, color: AppColors.onError),
            ),
          ),
        ),
      ],
    );
  }

  // ─── Bottom bar: Submit Complaint ─────────────────────────────────────────

  Widget _buildBottomBar() {
    final enabled = _canSubmit && !_isSubmitting;
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surface.withValues(alpha: 0.9),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 12,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: AnimatedContainer(
        duration: AppAnimations.pageTransition,
        curve: Curves.easeOut,
        height: 56,
        decoration: BoxDecoration(
          color: enabled ? AppColors.primary : AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(100),
          boxShadow: enabled
              ? [
                  BoxShadow(
                    color: AppColors.primary.withValues(alpha: 0.3),
                    blurRadius: 16,
                    offset: const Offset(0, 4),
                  ),
                ]
              : [],
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            borderRadius: BorderRadius.circular(100),
            onTap: enabled ? _submitComplaint : null,
            child: AnimatedSwitcher(
              duration: AppAnimations.modalOpen,
              switchInCurve: AppAnimations.modalOpenCurve,
              switchOutCurve: AppAnimations.modalCloseCurve,
              child: _isSubmitting
                  ? Row(
                      key: const ValueKey('submitting'),
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: AppColors.onPrimary,
                          ),
                        ),
                        const SizedBox(width: 10),
                        Text(
                          'Submitting...',
                          style: AppTypography.titleLarge.copyWith(
                            color: AppColors.onPrimary,
                          ),
                        ),
                      ],
                    )
                  : Row(
                      key: const ValueKey('idle'),
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.send,
                          size: 20,
                          color: enabled
                              ? AppColors.onPrimary
                              : AppColors.onSurfaceVariant,
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Submit Complaint',
                          style: AppTypography.titleLarge.copyWith(
                            color: enabled
                                ? AppColors.onPrimary
                                : AppColors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
            ),
          ),
        ),
      ),
    );
  }
}
