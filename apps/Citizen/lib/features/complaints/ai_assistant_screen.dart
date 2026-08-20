import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/services/ai_service.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/theme/app_radius.dart';

/// AI Assistant Screen — matches Stitch "AI Assistant | CivicFlow" design.
/// Features: pulsing AI orb header, chat bubbles, action chips,
/// typing indicator, suggestion bar, and bottom input.
class AiAssistantScreen extends StatefulWidget {
  const AiAssistantScreen({super.key});

  @override
  State<AiAssistantScreen> createState() => _AiAssistantScreenState();
}

class _AiAssistantScreenState extends State<AiAssistantScreen>
    with TickerProviderStateMixin {
  late AnimationController _orbPulse;
  late AnimationController _dotsCtrl;
  final ScrollController _scrollCtrl = ScrollController();
  final TextEditingController _inputCtrl = TextEditingController();
  final AiService _aiService = AiService();

  final List<Map<String, dynamic>> _messages = [
    {'text': 'Hello! How can I assist you with your complaint today?', 'isUser': false, 'time': ''},
  ];
  bool _isTyping = false;

  static const _categoryChips = ['Road Repair', 'Street Light', 'Water Issue'];

  @override
  void initState() {
    super.initState();
    _orbPulse = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 2000),
    )..repeat(reverse: true);

    _dotsCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat();
  }

  void _sendMessage(String text) async {
    if (text.trim().isEmpty) return;
    
    final now = DateTime.now();
    final time = '${now.hour}:${now.minute.toString().padLeft(2, '0')}';
    
    setState(() {
      _messages.add({'text': text, 'isUser': true, 'time': time});
      _isTyping = true;
    });
    _inputCtrl.clear();
    _scrollToBottom();

    try {
      final response = await _aiService.chat(text);
      if (mounted) {
        setState(() {
          _messages.add({'text': response.answer, 'isUser': false, 'time': time});
          _isTyping = false;
        });
        _scrollToBottom();
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _messages.add({'text': 'Sorry, I am having trouble connecting.', 'isUser': false, 'time': time});
          _isTyping = false;
        });
        _scrollToBottom();
      }
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollCtrl.hasClients) {
        _scrollCtrl.animateTo(
          _scrollCtrl.position.maxScrollExtent + 100,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  void dispose() {
    _orbPulse.dispose();
    _dotsCtrl.dispose();
    _scrollCtrl.dispose();
    _inputCtrl.dispose();
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
          'File New Grievance',
          style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: ResponsiveLayout.mobileBreakpoint),
            child: Column(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    controller: _scrollCtrl,
                    child: Column(
                      children: [
                        // AI Orb header
                        _buildOrbHeader(),
                        // Chat messages
                        Padding(
                          padding: const EdgeInsets.symmetric(
                            horizontal: AppSpacing.space2,
                          ),
                          child: Column(
                            children: [
                              ..._messages.map((msg) {
                                if (msg['isUser'] as bool) {
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 16),
                                    child: _buildUserBubble(
                                      text: msg['text'] as String,
                                      time: msg['time'] as String,
                                    ),
                                  );
                                } else {
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 16),
                                    child: _buildAiBubble(
                                      text: msg['text'] as String,
                                    ),
                                  );
                                }
                              }),
                              if (_isTyping) _buildTypingIndicator(),
                              const SizedBox(height: AppSpacing.space5),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                // Bottom input area
                _buildBottomInput(),
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
                      context.go('/home');
                    }
                  },
                ),
                const SizedBox(width: AppSpacing.space2),
                Text(
                  'File New Grievance',
                  style: AppTypography.headlineMedium.copyWith(color: AppColors.onSurface),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.space6),
            
            Expanded(
              child: Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 800),
                  child: Container(
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: AppRadius.borderRadiusXl,
                      border: Border.all(color: AppColors.outlineVariant.withValues(alpha: 0.5)),
                    ),
                    child: Column(
                      children: [
                        Expanded(
                          child: SingleChildScrollView(
                            controller: _scrollCtrl,
                            child: Column(
                              children: [
                                // AI Orb header
                                _buildOrbHeader(),
                                // Chat messages
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: AppSpacing.space6,
                                  ),
                                  child: Column(
                                    children: [
                                      ..._messages.map((msg) {
                                        if (msg['isUser'] as bool) {
                                          return Padding(
                                            padding: const EdgeInsets.only(bottom: 16),
                                            child: _buildUserBubble(
                                              text: msg['text'] as String,
                                              time: msg['time'] as String,
                                            ),
                                          );
                                        } else {
                                          return Padding(
                                            padding: const EdgeInsets.only(bottom: 16),
                                            child: _buildAiBubble(
                                              text: msg['text'] as String,
                                            ),
                                          );
                                        }
                                      }),
                                      if (_isTyping) _buildTypingIndicator(),
                                      const SizedBox(height: AppSpacing.space5),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        // Bottom input area
                        _buildBottomInput(),
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

  Widget _buildOrbHeader() {
    return SizedBox(
      height: 180,
      child: Stack(
        children: [
          // Ambient gradient
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    AppColors.primary.withValues(alpha: 0.08),
                    Colors.transparent,
                  ],
                ),
              ),
            ),
          ),
          // Pulsing orb
          Center(
            child: AnimatedBuilder(
              animation: _orbPulse,
              builder: (context, _) {
                final scale = 1.0 + _orbPulse.value * 0.08;
                return Transform.scale(
                  scale: scale,
                  child: Container(
                    width: 64,
                    height: 64,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color:
                          AppColors.primaryContainer.withValues(alpha: 0.2),
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.primary.withValues(
                              alpha: 0.1 + _orbPulse.value * 0.1),
                          blurRadius: 24,
                          spreadRadius: 4,
                        ),
                      ],
                    ),
                    child: const Icon(
                      Icons.smart_toy,
                      color: AppColors.primary,
                      size: 28,
                    ),
                  ),
                );
              },
            ),
          ),
          // Subtitle
          Positioned(
            bottom: 16,
            left: 0,
            right: 0,
            child: Text(
              'How can I assist you with your\ncomplaint today?',
              textAlign: TextAlign.center,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUserBubble({required String text, required String time}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Align(
          alignment: Alignment.centerRight,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width * 0.75,
            ),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: const BoxDecoration(
                color: AppColors.primary,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(16),
                  topRight: Radius.circular(4),
                  bottomLeft: Radius.circular(16),
                  bottomRight: Radius.circular(16),
                ),
              ),
              child: Text(
                text,
                style: AppTypography.bodyMedium.copyWith(
                  color: AppColors.onPrimary,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 4),
        Padding(
          padding: const EdgeInsets.only(right: 8),
          child: Text(
            time,
            style: AppTypography.caption.copyWith(color: AppColors.outline),
          ),
        ),
      ],
    );
  }

  Widget _buildAiBubble({required String text}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        // Avatar
        Container(
          width: 32,
          height: 32,
          margin: const EdgeInsets.only(bottom: 4),
          decoration: const BoxDecoration(
            color: AppColors.secondaryContainer,
            shape: BoxShape.circle,
          ),
          child: const Icon(
            Icons.psychology,
            color: AppColors.onSecondaryContainer,
            size: 16,
          ),
        ),
        const SizedBox(width: 8),
        Flexible(
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainer,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(4),
                topRight: Radius.circular(16),
                bottomLeft: Radius.circular(16),
                bottomRight: Radius.circular(16),
              ),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.04),
                  blurRadius: 6,
                ),
              ],
            ),
            child: Text(
              text,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.onSurface,
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildActionChips() {
    return Padding(
      padding: const EdgeInsets.only(left: 44), // align with AI bubble content
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            _PressableButton(
              onPressed: () {},
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(100),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.06),
                      blurRadius: 4,
                    ),
                  ],
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.location_on,
                        size: 18, color: AppColors.primary),
                    const SizedBox(width: 6),
                    Text(
                      'Use Current Location',
                      style: AppTypography.labelSmall.copyWith(
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 8),
            _PressableButton(
              onPressed: () {},
              child: Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(100),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.06),
                      blurRadius: 4,
                    ),
                  ],
                ),
                child: Text(
                  'Near Main St. Intersection',
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.onSurface,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTypingIndicator() {
    return Padding(
      padding: const EdgeInsets.only(left: 44),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Opacity(
          opacity: 0.7,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Bouncing dots
              AnimatedBuilder(
                animation: _dotsCtrl,
                builder: (context, _) {
                  return Row(
                    children: List.generate(3, (i) {
                      final delay = i * 0.16;
                      final t = (_dotsCtrl.value - delay).clamp(0.0, 1.0);
                      final bounce = (1 - (2 * t - 1).abs()) * 4;
                      return Container(
                        margin: const EdgeInsets.symmetric(horizontal: 2),
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: AppColors.primary,
                          shape: BoxShape.circle,
                        ),
                        transform: Matrix4.translationValues(0, -bounce, 0),
                      );
                    }),
                  );
                },
              ),
              const SizedBox(width: 8),
              Text(
                'Analyzing context...',
                style: AppTypography.caption.copyWith(
                  color: AppColors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBottomInput() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerLowest.withValues(alpha: 0.92),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 24,
            offset: const Offset(0, -4),
          ),
        ],
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Category chips row
          Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.space2,
              vertical: 8,
            ),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _categoryChips.map((chip) {
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 16, vertical: 6),
                      decoration: BoxDecoration(
                        color: AppColors.secondaryContainer.withValues(alpha: 0.5),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        chip,
                        style: AppTypography.labelSmall.copyWith(
                          color: AppColors.onSecondaryContainer,
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ),
          // Input row
          Padding(
            padding: const EdgeInsets.fromLTRB(
              AppSpacing.space2,
              0,
              AppSpacing.space2,
              AppSpacing.space2,
            ),
            child: Row(
              children: [
                // Camera button
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppColors.surfaceContainer,
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    icon: const Icon(Icons.add_a_photo,
                        color: AppColors.onSurfaceVariant, size: 22),
                    onPressed: () {},
                  ),
                ),
                const SizedBox(width: 8),
                // Text field
                Expanded(
                  child: Focus(
                    child: Builder(builder: (context) {
                      final focused = Focus.of(context).hasFocus;
                      return AnimatedContainer(
                        duration: AppAnimations.buttonPress,
                        height: 48,
                        decoration: BoxDecoration(
                          color: AppColors.surfaceContainer,
                          borderRadius: BorderRadius.circular(100),
                          border: Border.all(
                            color: focused ? AppColors.primary : Colors.transparent,
                            width: 2,
                          ),
                        ),
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: TextField(
                      controller: _inputCtrl,
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.onSurface,
                      ),
                      decoration: InputDecoration(
                        hintText: 'Type or say something...',
                        hintStyle: AppTypography.bodyMedium.copyWith(
                          color: AppColors.outlineVariant,
                        ),
                        border: InputBorder.none,
                        contentPadding: EdgeInsets.zero,
                        isDense: true,
                      ),
                      onSubmitted: _sendMessage,
                    ),
                  );
                    }),
                  ),
                ),
                const SizedBox(width: 8),
                // Mic button
                _PressableButton(
                  onPressed: () => _sendMessage(_inputCtrl.text),
                  child: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: AppColors.primary,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.primary.withValues(alpha: 0.3),
                          blurRadius: 12,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        // Subtle gradient overlay
                        Container(
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            gradient: LinearGradient(
                              begin: Alignment.bottomCenter,
                              end: Alignment.topCenter,
                              colors: [
                                Colors.black.withValues(alpha: 0.1),
                                Colors.transparent,
                              ],
                            ),
                          ),
                        ),
                        const Icon(Icons.send, color: AppColors.onPrimary, size: 20),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
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
    _scale = Tween<double>(begin: 1.0, end: 0.95).animate(
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
      onTapDown: (_) => _ctrl.forward(),
      onTapUp: (_) {
        _ctrl.reverse();
        widget.onPressed?.call();
      },
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(scale: _scale, child: widget.child),
    );
  }
}
