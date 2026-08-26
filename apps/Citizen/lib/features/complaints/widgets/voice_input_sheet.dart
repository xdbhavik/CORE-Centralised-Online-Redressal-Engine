import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_typography.dart';
import '../../../services/voice_service.dart';

/// Shows a modal bottom sheet that records the citizen's voice and transcribes
/// it via the backend STT API. Returns the transcript (or null if cancelled /
/// failed) so the caller can fill it into a text field.
Future<String?> showVoiceInputSheet(BuildContext context) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (_) => const VoiceInputSheet(),
  );
}

enum _SheetState { checkingPermission, permissionDenied, idle, recording, transcribing, error }

class VoiceInputSheet extends StatefulWidget {
  const VoiceInputSheet({super.key});

  @override
  State<VoiceInputSheet> createState() => _VoiceInputSheetState();
}

class _VoiceInputSheetState extends State<VoiceInputSheet>
    with SingleTickerProviderStateMixin {
  final VoiceService _voiceService = VoiceService();

  _SheetState _state = _SheetState.checkingPermission;
  String? _errorMessage;

  Timer? _recordingTimer;
  int _recordingSeconds = 0;
  static const int _maxRecordingSeconds = 60;

  late AnimationController _pulseCtrl;

  @override
  void initState() {
    super.initState();
    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );
    _checkPermission();
  }

  @override
  void dispose() {
    _recordingTimer?.cancel();
    _pulseCtrl.dispose();
    _voiceService.dispose();
    super.dispose();
  }

  Future<void> _checkPermission() async {
    final has = await _voiceService.hasPermission();
    if (mounted) {
      setState(() {
        _state = has ? _SheetState.idle : _SheetState.permissionDenied;
      });
    }
  }

  Future<void> _startRecording() async {
    try {
      await _voiceService.startRecording();
      setState(() {
        _state = _SheetState.recording;
        _recordingSeconds = 0;
        _errorMessage = null;
      });
      _pulseCtrl.repeat(reverse: true);
      _recordingTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted) {
          timer.cancel();
          return;
        }
        setState(() => _recordingSeconds++);
        if (_recordingSeconds >= _maxRecordingSeconds) {
          _stopAndTranscribe();
        }
      });
    } catch (_) {
      setState(() {
        _state = _SheetState.error;
        _errorMessage = 'Could not start recording. Check microphone permission.';
      });
    }
  }

  Future<void> _stopAndTranscribe() async {
    _recordingTimer?.cancel();
    _pulseCtrl.stop();
    _pulseCtrl.reset();

    File? file;
    try {
      file = await _voiceService.stopRecording();
    } catch (_) {
      file = null;
    }

    if (file == null) {
      if (mounted) {
        setState(() {
          _state = _SheetState.error;
          _errorMessage = 'Recording failed. Please try again.';
        });
      }
      return;
    }

    if (mounted) setState(() => _state = _SheetState.transcribing);

    final transcript = await _voiceService.transcribeAudio(file);
    if (!mounted) return;

    if (transcript != null && transcript.trim().isNotEmpty) {
      Navigator.of(context).pop(transcript.trim());
    } else {
      setState(() {
        _state = _SheetState.error;
        _errorMessage =
            'Could not understand the audio. Please speak clearly and try again.';
      });
    }
  }

  String _formatDuration(int totalSeconds) {
    final mins = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final secs = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$mins:$secs';
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: EdgeInsets.only(
        left: AppSpacing.space3,
        right: AppSpacing.space3,
        top: AppSpacing.space3,
        bottom: MediaQuery.of(context).viewInsets.bottom + AppSpacing.space4,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.outlineVariant,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Speak Your Complaint',
            style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
          ),
          const SizedBox(height: 4),
          Text(
            'Your voice will be converted to text and filled into the description.',
            style: AppTypography.caption.copyWith(color: AppColors.onSurfaceVariant),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.space4),
          _buildBody(),
          if (_errorMessage != null) ...[
            const SizedBox(height: AppSpacing.space2),
            Container(
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
                      style: AppTypography.caption
                          .copyWith(color: AppColors.onErrorContainer),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (_state == _SheetState.idle || _state == _SheetState.error) ...[
            const SizedBox(height: AppSpacing.space2),
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: Text(
                'Cancel',
                style: AppTypography.bodyMedium
                    .copyWith(color: AppColors.onSurfaceVariant),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildBody() {
    switch (_state) {
      case _SheetState.checkingPermission:
        return const Padding(
          padding: EdgeInsets.all(AppSpacing.space4),
          child: CircularProgressIndicator(color: AppColors.primary),
        );

      case _SheetState.permissionDenied:
        return Column(
          children: [
            const Icon(Icons.mic_off, color: AppColors.error, size: 40),
            const SizedBox(height: AppSpacing.space2),
            Text(
              'Microphone access is needed for voice input.',
              style: AppTypography.bodyMedium
                  .copyWith(color: AppColors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.space2),
            TextButton(
              onPressed: _checkPermission,
              child: Text('Try Again',
                  style: AppTypography.bodyMedium
                      .copyWith(color: AppColors.primary)),
            ),
          ],
        );

      case _SheetState.idle:
      case _SheetState.error:
        return GestureDetector(
          onTap: _startRecording,
          child: Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withValues(alpha: 0.3),
                  blurRadius: 20,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: const Icon(Icons.mic, color: AppColors.onPrimary, size: 36),
          ),
        );

      case _SheetState.recording:
        return Column(
          children: [
            AnimatedBuilder(
              animation: _pulseCtrl,
              builder: (context, child) {
                final scale = 1.0 + _pulseCtrl.value * 0.15;
                return Transform.scale(scale: scale, child: child);
              },
              child: GestureDetector(
                onTap: _stopAndTranscribe,
                child: Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    color: AppColors.error,
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.error.withValues(alpha: 0.3),
                        blurRadius: 20,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: const Icon(Icons.stop_rounded,
                      color: AppColors.onError, size: 40),
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.space2),
            Text(
              _formatDuration(_recordingSeconds),
              style: AppTypography.titleLarge.copyWith(
                color: AppColors.onSurface,
                fontWeight: FontWeight.w700,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: 4),
            Text(
              'Recording... tap to stop',
              style: AppTypography.caption
                  .copyWith(color: AppColors.onSurfaceVariant),
            ),
          ],
        );

      case _SheetState.transcribing:
        return Padding(
          padding: const EdgeInsets.all(AppSpacing.space3),
          child: Column(
            children: [
              const CircularProgressIndicator(
                  color: AppColors.primary, strokeWidth: 3),
              const SizedBox(height: AppSpacing.space2),
              Text(
                'Converting speech to text...',
                style: AppTypography.bodyMedium
                    .copyWith(color: AppColors.onSurfaceVariant),
              ),
            ],
          ),
        );
    }
  }
}
