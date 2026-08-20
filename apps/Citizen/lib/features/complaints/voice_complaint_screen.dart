import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:audioplayers/audioplayers.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/theme/app_radius.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../services/voice_service.dart';

/// Voice Complaint Screen — real audio recording + STT transcription.
/// States: Permission Denied → Idle → Recording → Recorded → Uploading → Success/Error
class VoiceComplaintScreen extends StatefulWidget {
  const VoiceComplaintScreen({super.key});

  @override
  State<VoiceComplaintScreen> createState() => _VoiceComplaintScreenState();
}

enum _VoiceState { checkingPermission, permissionDenied, idle, recording, recorded, transcribing, success, error }

class _VoiceComplaintScreenState extends State<VoiceComplaintScreen>
    with TickerProviderStateMixin {
  final VoiceService _voiceService = VoiceService();
  final AudioPlayer _audioPlayer = AudioPlayer();

  _VoiceState _state = _VoiceState.checkingPermission;
  File? _recordedFile;
  String? _transcript;
  String? _errorMessage;

  // Recording timer
  Timer? _recordingTimer;
  int _recordingSeconds = 0;
  static const int _maxRecordingSeconds = 120; // 2 minutes
  static const int _warningSeconds = 110; // 10 sec before limit

  // Playback
  bool _isPlaying = false;
  Duration _playbackPosition = Duration.zero;
  Duration _playbackDuration = Duration.zero;

  // Waveform simulation
  static const int _numBars = 12;
  final List<double> _barHeights = List.filled(_numBars, 0.1);
  final math.Random _rand = math.Random();
  Timer? _waveTimer;

  // Animations
  late AnimationController _pulseCtrl;
  late AnimationController _playbackEntranceCtrl;
  late Animation<double> _playbackFade;
  late Animation<Offset> _playbackSlide;
  late AnimationController _errorCtrl;

  @override
  void initState() {
    super.initState();

    _pulseCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );

    _playbackEntranceCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
    _playbackFade = CurvedAnimation(
      parent: _playbackEntranceCtrl,
      curve: Curves.easeOutCubic,
    );
    _playbackSlide = Tween<Offset>(
      begin: const Offset(0, 0.06),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _playbackEntranceCtrl,
      curve: Curves.easeOutCubic,
    ));

    _errorCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );

    // Listen to audio player state
    _audioPlayer.onPositionChanged.listen((pos) {
      if (mounted) setState(() => _playbackPosition = pos);
    });
    _audioPlayer.onDurationChanged.listen((dur) {
      if (mounted) setState(() => _playbackDuration = dur);
    });
    _audioPlayer.onPlayerComplete.listen((_) {
      if (mounted) setState(() => _isPlaying = false);
    });

    _checkPermission();
  }

  @override
  void dispose() {
    _recordingTimer?.cancel();
    _waveTimer?.cancel();
    _pulseCtrl.dispose();
    _playbackEntranceCtrl.dispose();
    _errorCtrl.dispose();
    _audioPlayer.dispose();
    _voiceService.dispose();
    super.dispose();
  }

  Future<void> _checkPermission() async {
    final has = await _voiceService.hasPermission();
    if (mounted) {
      setState(() {
        _state = has ? _VoiceState.idle : _VoiceState.permissionDenied;
      });
    }
  }

  Future<void> _startRecording() async {
    try {
      await _voiceService.startRecording();
      setState(() {
        _state = _VoiceState.recording;
        _recordingSeconds = 0;
        _errorMessage = null;
      });

      _pulseCtrl.repeat(reverse: true);
      _startWaveAnimation();

      _recordingTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted) {
          timer.cancel();
          return;
        }
        setState(() => _recordingSeconds++);
        if (_recordingSeconds >= _maxRecordingSeconds) {
          _stopRecording();
        }
      });
    } catch (e) {
      setState(() {
        _state = _VoiceState.error;
        _errorMessage = 'Failed to start recording. Check microphone permissions.';
      });
      _errorCtrl.forward(from: 0);
    }
  }

  void _startWaveAnimation() {
    _waveTimer = Timer.periodic(const Duration(milliseconds: 80), (_) {
      if (!mounted || _state != _VoiceState.recording) return;
      final t = DateTime.now().millisecondsSinceEpoch / 1000.0 * 2 * math.pi;
      setState(() {
        for (int i = 0; i < _numBars; i++) {
          double noise = (math.sin(t + i) * 0.5 + 0.5);
          double bump =
              _rand.nextDouble() > 0.8 ? _rand.nextDouble() * 0.4 : 0;
          double h = 0.1 + noise * 0.6 + bump;
          if (i == 0 || i == _numBars - 1) h *= 0.3;
          if (i == 1 || i == _numBars - 2) h *= 0.6;
          _barHeights[i] = h.clamp(0.05, 1.0);
        }
      });
    });
  }

  Future<void> _stopRecording() async {
    _recordingTimer?.cancel();
    _waveTimer?.cancel();
    _pulseCtrl.stop();
    _pulseCtrl.reset();

    try {
      final file = await _voiceService.stopRecording();
      if (file != null && mounted) {
        setState(() {
          _recordedFile = file;
          _state = _VoiceState.recorded;
        });
        _playbackEntranceCtrl.forward(from: 0);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _state = _VoiceState.error;
          _errorMessage = 'Failed to stop recording.';
        });
        _errorCtrl.forward(from: 0);
      }
    }
  }

  void _reRecord() {
    _audioPlayer.stop();
    setState(() {
      _recordedFile = null;
      _transcript = null;
      _isPlaying = false;
      _playbackPosition = Duration.zero;
      _playbackDuration = Duration.zero;
      _state = _VoiceState.idle;
    });
  }

  Future<void> _togglePlayback() async {
    if (_recordedFile == null) return;
    if (_isPlaying) {
      await _audioPlayer.pause();
      setState(() => _isPlaying = false);
    } else {
      await _audioPlayer.play(DeviceFileSource(_recordedFile!.path));
      setState(() => _isPlaying = true);
    }
  }

  Future<void> _submitVoiceComplaint() async {
    if (_recordedFile == null) return;
    setState(() {
      _state = _VoiceState.transcribing;
      _errorMessage = null;
    });

    try {
      final transcript = await _voiceService.transcribeAudio(_recordedFile!);
      if (transcript != null && transcript.isNotEmpty && mounted) {
        setState(() {
          _transcript = transcript;
          _state = _VoiceState.success;
        });

        // Navigate to AI Review with the transcript as description
        await Future.delayed(const Duration(milliseconds: 500));
        if (mounted) {
          final draft = ComplaintDraft(description: transcript);
          context.push('/ai-review', extra: draft);
        }
      } else if (mounted) {
        setState(() {
          _state = _VoiceState.error;
          _errorMessage = 'Transcription returned empty. Please try again.';
        });
        _errorCtrl.forward(from: 0);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _state = _VoiceState.error;
          _errorMessage = 'Something went wrong. Please try again.';
        });
        _errorCtrl.forward(from: 0);
      }
    }
  }

  String _formatDuration(int totalSeconds) {
    final mins = (totalSeconds ~/ 60).toString().padLeft(2, '0');
    final secs = (totalSeconds % 60).toString().padLeft(2, '0');
    return '$mins:$secs';
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
          'Voice Complaint',
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
                  'Voice Complaint',
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
            child: _buildMainArea(),
          ),
          if (_errorMessage != null) _buildErrorMessage(),
          _buildBottomActions(),
        ],
      ),
    );
  }

  Widget _buildMainArea() {
    switch (_state) {
      case _VoiceState.checkingPermission:
        return const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        );

      case _VoiceState.permissionDenied:
        return _buildPermissionDenied();

      case _VoiceState.idle:
      case _VoiceState.error:
        return _buildIdleState();

      case _VoiceState.recording:
        return _buildRecordingState();

      case _VoiceState.recorded:
        return _buildRecordedState();

      case _VoiceState.transcribing:
        return _buildTranscribingState();

      case _VoiceState.success:
        return _buildSuccessState();
    }
  }

  Widget _buildPermissionDenied() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: AppColors.errorContainer.withValues(alpha: 0.4),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.mic_off,
                color: AppColors.error,
                size: 36,
              ),
            ),
            const SizedBox(height: AppSpacing.space4),
            Text(
              'Microphone Access Required',
              style: AppTypography.titleLarge.copyWith(
                color: AppColors.onSurface,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.space2),
            Text(
              'CORE needs microphone access to record your voice complaint. '
              'Please grant permission in your device settings.',
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.space4),
            _PressableButton(
              onPressed: _checkPermission,
              child: Container(
                padding: const EdgeInsets.symmetric(
                  horizontal: 24,
                  vertical: 14,
                ),
                decoration: BoxDecoration(
                  color: AppColors.primary,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'Try Again',
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.onPrimary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildIdleState() {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Status badge
        Container(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.space2,
            vertical: AppSpacing.space1,
          ),
          decoration: BoxDecoration(
            color: AppColors.surfaceContainerHigh,
            borderRadius: BorderRadius.circular(100),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  color: AppColors.onSurfaceVariant,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: AppSpacing.space1),
              Text(
                'Ready to Record',
                style: AppTypography.caption.copyWith(
                  color: AppColors.onSurface,
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppSpacing.space8),

        // Record button
        GestureDetector(
          onTap: _startRecording,
          child: Container(
            width: 96,
            height: 96,
            decoration: BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
              boxShadow: [
                BoxShadow(
                  color: AppColors.primary.withValues(alpha: 0.3),
                  blurRadius: 24,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: const Icon(
              Icons.mic,
              color: AppColors.onPrimary,
              size: 40,
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.space4),
        Text(
          'Tap to start recording',
          style: AppTypography.bodyMedium.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: AppSpacing.space1),
        Text(
          'Maximum ${_maxRecordingSeconds ~/ 60} minutes',
          style: AppTypography.caption.copyWith(
            color: AppColors.onSurfaceVariant.withValues(alpha: 0.7),
          ),
        ),
      ],
    );
  }

  Widget _buildRecordingState() {
    final isWarning = _recordingSeconds >= _warningSeconds;
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        // Status badge
        AnimatedBuilder(
          animation: _pulseCtrl,
          builder: (context, _) {
            return Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.space2,
                vertical: AppSpacing.space1,
              ),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerHigh,
                borderRadius: BorderRadius.circular(100),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: AppColors.error.withValues(
                        alpha: 0.5 + _pulseCtrl.value * 0.5,
                      ),
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: AppSpacing.space1),
                  Text(
                    'Recording...',
                    style: AppTypography.caption.copyWith(
                      color: AppColors.onSurface,
                    ),
                  ),
                ],
              ),
            );
          },
        ),
        const SizedBox(height: AppSpacing.space5),

        // Waveform with pulse rings
        SizedBox(
          height: 160,
          child: AnimatedBuilder(
            animation: _pulseCtrl,
            builder: (context, child) {
              return Stack(
                alignment: Alignment.center,
                children: [
                  // Outer pulse
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 50),
                    width: 192 + _pulseCtrl.value * 12,
                    height: 192 + _pulseCtrl.value * 12,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color:
                          AppColors.primaryFixed.withValues(alpha: 0.12),
                    ),
                  ),
                  // Inner pulse
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 50),
                    width: 128 + _pulseCtrl.value * 8,
                    height: 128 + _pulseCtrl.value * 8,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color:
                          AppColors.primaryFixed.withValues(alpha: 0.22),
                    ),
                  ),
                  // Bars
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.center,
                    children: List.generate(_numBars, (i) {
                      return Container(
                        margin: const EdgeInsets.symmetric(horizontal: 3),
                        width: 10,
                        height: _barHeights[i] * 96,
                        decoration: BoxDecoration(
                          color: AppColors.primary,
                          borderRadius: BorderRadius.circular(5),
                        ),
                      );
                    }),
                  ),
                ],
              );
            },
          ),
        ),
        const SizedBox(height: AppSpacing.space4),

        // Timer
        Text(
          _formatDuration(_recordingSeconds),
          style: AppTypography.headlineMedium.copyWith(
            color: isWarning ? AppColors.error : AppColors.onSurface,
            fontWeight: FontWeight.w700,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),

        // Warning message
        if (isWarning) ...[
          const SizedBox(height: AppSpacing.space1),
          Text(
            'Recording will stop soon',
            style: AppTypography.caption.copyWith(
              color: AppColors.error,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],

        const SizedBox(height: AppSpacing.space6),

        // Stop button
        GestureDetector(
          onTap: _stopRecording,
          child: AnimatedBuilder(
            animation: _pulseCtrl,
            builder: (context, child) {
              final scale = 1.0 + _pulseCtrl.value * 0.15;
              return Transform.scale(scale: scale, child: child);
            },
            child: Container(
              width: 96,
              height: 96,
              decoration: BoxDecoration(
                color: AppColors.error,
                shape: BoxShape.circle,
                boxShadow: [
                  BoxShadow(
                    color: AppColors.error.withValues(alpha: 0.3),
                    blurRadius: 24,
                    offset: const Offset(0, 8),
                  ),
                ],
              ),
              child: const Icon(
                Icons.stop_rounded,
                color: AppColors.onError,
                size: 44,
              ),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.space3),
        Text(
          'Tap to stop',
          style: AppTypography.bodyMedium.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
      ],
    );
  }

  Widget _buildRecordedState() {
    return FadeTransition(
      opacity: _playbackFade,
      child: SlideTransition(
        position: _playbackSlide,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Checkmark badge
            Container(
              padding: const EdgeInsets.symmetric(
                horizontal: AppSpacing.space2,
                vertical: AppSpacing.space1,
              ),
              decoration: BoxDecoration(
                color: const Color(0xFF10B981).withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(100),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.check_circle,
                      size: 14, color: Color(0xFF10B981)),
                  const SizedBox(width: 4),
                  Text(
                    'Recording Complete',
                    style: AppTypography.caption.copyWith(
                      color: const Color(0xFF10B981),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.space4),

            // Duration display
            Text(
              _formatDuration(_recordingSeconds),
              style: AppTypography.headlineMedium.copyWith(
                color: AppColors.onSurface,
                fontWeight: FontWeight.w700,
                fontFeatures: const [FontFeature.tabularFigures()],
              ),
            ),
            const SizedBox(height: AppSpacing.space4),

            // Playback card
            Container(
              padding: const EdgeInsets.all(AppSpacing.space3),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Row(
                children: [
                  // Play / Pause button
                  GestureDetector(
                    onTap: _togglePlayback,
                    child: Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.primary,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        _isPlaying ? Icons.pause : Icons.play_arrow,
                        color: AppColors.onPrimary,
                        size: 24,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.space2),
                  // Scrubber / progress bar
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        ClipRRect(
                          borderRadius: BorderRadius.circular(4),
                          child: LinearProgressIndicator(
                            value: _playbackDuration.inMilliseconds > 0
                                ? _playbackPosition.inMilliseconds /
                                    _playbackDuration.inMilliseconds
                                : 0,
                            backgroundColor: AppColors.surfaceVariant,
                            color: AppColors.primary,
                            minHeight: 6,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Row(
                          mainAxisAlignment:
                              MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              _formatDuration(
                                  _playbackPosition.inSeconds),
                              style: AppTypography.caption.copyWith(
                                color: AppColors.onSurfaceVariant,
                                fontFeatures: const [
                                  FontFeature.tabularFigures()
                                ],
                              ),
                            ),
                            Text(
                              _formatDuration(
                                  _playbackDuration.inSeconds > 0
                                      ? _playbackDuration.inSeconds
                                      : _recordingSeconds),
                              style: AppTypography.caption.copyWith(
                                color: AppColors.onSurfaceVariant,
                                fontFeatures: const [
                                  FontFeature.tabularFigures()
                                ],
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: AppSpacing.space4),

            // Re-record option
            GestureDetector(
              onTap: _reRecord,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.replay,
                      color: AppColors.onSurfaceVariant, size: 18),
                  const SizedBox(width: 4),
                  Text(
                    'Re-record',
                    style: AppTypography.bodyMedium.copyWith(
                      color: AppColors.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTranscribingState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const CircularProgressIndicator(
            color: AppColors.primary,
            strokeWidth: 3,
          ),
          const SizedBox(height: AppSpacing.space4),
          Text(
            'Transcribing your voice...',
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          const SizedBox(height: AppSpacing.space1),
          Text(
            'AI is converting your speech to text',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSuccessState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 80,
            height: 80,
            decoration: BoxDecoration(
              color: const Color(0xFF10B981).withValues(alpha: 0.15),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.check_circle,
              color: Color(0xFF10B981),
              size: 40,
            ),
          ),
          const SizedBox(height: AppSpacing.space4),
          Text(
            'Transcription Complete',
            style: AppTypography.titleLarge.copyWith(
              color: AppColors.onSurface,
            ),
          ),
          if (_transcript != null) ...[
            const SizedBox(height: AppSpacing.space2),
            Container(
              padding: const EdgeInsets.all(AppSpacing.space3),
              decoration: BoxDecoration(
                color: AppColors.surfaceContainerLow,
                borderRadius: BorderRadius.circular(12),
              ),
              constraints: const BoxConstraints(maxHeight: 120),
              child: SingleChildScrollView(
                child: Text(
                  _transcript!,
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          ],
          const SizedBox(height: AppSpacing.space3),
          Text(
            'Redirecting...',
            style: AppTypography.caption.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ],
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

  Widget _buildBottomActions() {
    if (_state == _VoiceState.recording) {
      return const SizedBox.shrink(); // Stop button is inline
    }

    if (_state == _VoiceState.recorded) {
      return _PressableButton(
        onPressed: _submitVoiceComplaint,
        child: Container(
          width: double.infinity,
          height: 56,
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(12),
            boxShadow: [
              BoxShadow(
                color: AppColors.primary.withValues(alpha: 0.3),
                blurRadius: 16,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.send,
                  color: AppColors.onPrimary, size: 20),
              const SizedBox(width: 8),
              Text(
                'Submit Voice Complaint',
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.onPrimary,
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                ),
              ),
            ],
          ),
        ),
      );
    }

    if (_state == _VoiceState.transcribing) {
      return AnimatedOpacity(
        opacity: 0.4,
        duration: const Duration(milliseconds: 200),
        child: Container(
          width: double.infinity,
          height: 56,
          decoration: BoxDecoration(
            color: AppColors.primary,
            borderRadius: BorderRadius.circular(12),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(
                  color: AppColors.onPrimary,
                  strokeWidth: 2,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                'Transcribing...',
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.onPrimary,
                  fontWeight: FontWeight.w600,
                  fontSize: 14,
                ),
              ),
            ],
          ),
        ),
      );
    }

    // Cancel button for idle / permission denied / error / success
    return GestureDetector(
      onTap: () {
        if (GoRouter.of(context).canPop()) {
          context.pop();
        } else {
          context.go('/home');
        }
      },
      child: SizedBox(
        height: 48,
        child: Center(
          child: Text(
            'Cancel',
            style: AppTypography.bodyMedium.copyWith(
              color: AppColors.onSurfaceVariant,
            ),
          ),
        ),
      ),
    );
  }
}

/// Generic pressable wrapper that scales down on press
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
