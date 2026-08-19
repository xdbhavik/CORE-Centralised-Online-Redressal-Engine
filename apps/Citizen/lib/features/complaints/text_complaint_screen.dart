import 'dart:async';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:file_picker/file_picker.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/app_typography.dart';
import '../../core/theme/app_animations.dart';
import '../../core/services/ai_service.dart';
import '../../core/models/complaint.dart';
import '../../core/widgets/responsive_layout.dart';
import '../../core/widgets/desktop_sidebar.dart';
import '../../core/widgets/complaint_map_widget.dart';

class TextComplaintScreen extends StatefulWidget {
  const TextComplaintScreen({super.key});

  @override
  State<TextComplaintScreen> createState() => _TextComplaintScreenState();
}

class _TextComplaintScreenState extends State<TextComplaintScreen>
    with SingleTickerProviderStateMixin {
  final TextEditingController _textCtrl = TextEditingController();
  bool _showAiCard = false;
  String _aiCategory = 'Analyzing context...';
  List<String> _aiTags = [];
  int _confidence = 0;
  bool _analyzing = false;
  Timer? _typingTimer;

  final AiService _aiService = AiService();
  AIResponse? _aiResponse;
  
  double? _latitude;
  double? _longitude;
  String? _selectedFilePath;
  String? _selectedFileName;

  late AnimationController _aiCardCtrl;
  late Animation<double> _aiCardFade;
  late Animation<Offset> _aiCardSlide;

  static const _suggestions = [
    'Streetlight out',
    'Pothole',
    'Noise complaint',
    'Garbage uncollected',
  ];

  @override
  void initState() {
    super.initState();
    _aiCardCtrl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 300),
    );
    _aiCardFade = CurvedAnimation(parent: _aiCardCtrl, curve: Curves.easeOut);
    _aiCardSlide = Tween<Offset>(
      begin: const Offset(0, 0.05),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _aiCardCtrl, curve: Curves.easeOut));
    _textCtrl.addListener(_onTextChanged);
  }

  @override
  void dispose() {
    _typingTimer?.cancel();
    _textCtrl.dispose();
    _aiCardCtrl.dispose();
    super.dispose();
  }

  void _onTextChanged() {
    setState(() {});
    final text = _textCtrl.text;

    if (text.isEmpty) {
      _hideAiCard();
      return;
    }

    if (!_showAiCard) {
      setState(() {
        _showAiCard = true;
        _analyzing = true;
        _aiCategory = 'Analyzing context...';
        _aiTags = [];
      });
      _aiCardCtrl.forward();
    } else {
      setState(() {
        _analyzing = true;
        _aiCategory = 'Analyzing context...';
        _aiTags = [];
      });
    }

    _typingTimer?.cancel();
    _typingTimer = Timer(const Duration(milliseconds: 800), () {
      if (mounted) _runAnalysis(text);
    });
  }

  void _hideAiCard() {
    _aiCardCtrl.reverse().then((_) {
      if (mounted) setState(() => _showAiCard = false);
    });
  }

  Future<void> _runAnalysis(String text) async {
    try {
      final response = await _aiService.analyzePreview(description: text);
      if (mounted) {
        setState(() {
          _analyzing = false;
          _aiResponse = response;
          if (response.department != null && response.category != null) {
            _aiCategory = 'Looks like a ${response.category} issue for ${response.department}.';
          } else {
            _aiCategory = 'Needs more detail to route correctly.';
          }
          _aiTags = [
            if (response.category != null) response.category!,
            if (response.priority != null) response.priority!,
          ];
          _confidence = response.confidence ?? 0;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _analyzing = false;
          _aiCategory = 'Could not analyze context.';
          _aiTags = [];
          _confidence = 0;
        });
      }
    }
  }

  Future<void> _pickFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['jpg', 'png', 'jpeg', 'mp4', 'pdf'],
      );
      if (result != null && result.files.single.path != null) {
        setState(() {
          _selectedFilePath = result.files.single.path;
          _selectedFileName = result.files.single.name;
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to pick file.')),
        );
      }
    }
  }

  bool get _canSubmit => _textCtrl.text.length > 15 && _latitude != null && _longitude != null;

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
            child: Stack(
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space2,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: AppSpacing.space3),
                      Text('Describe the issue',
                          style: AppTypography.headlineMedium.copyWith(
                            fontSize: 26,
                            fontWeight: FontWeight.w600,
                          )),
                      const SizedBox(height: 4),
                      Text(
                        'Be as specific as possible so we can route it correctly.',
                        style: AppTypography.bodyMedium.copyWith(
                          color: AppColors.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: AppSpacing.space3),
                      // AI Analysis card
                      if (_showAiCard) ...[
                        SlideTransition(
                          position: _aiCardSlide,
                          child: FadeTransition(
                            opacity: _aiCardFade,
                            child: _buildAiCard(),
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space2),
                      ],
                      // Suggestion chips
                      _buildSuggestionChips(),
                      const SizedBox(height: AppSpacing.space2),
                      
                      // Selected file indicator
                      if (_selectedFileName != null) ...[
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                          decoration: BoxDecoration(
                            color: AppColors.primaryContainer.withValues(alpha: 0.3),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.attach_file, size: 16, color: AppColors.primary),
                              const SizedBox(width: 8),
                              Flexible(
                                child: Text(
                                  _selectedFileName!,
                                  style: AppTypography.caption.copyWith(color: AppColors.primary),
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                              ),
                              const SizedBox(width: 8),
                              GestureDetector(
                                onTap: () => setState(() {
                                  _selectedFilePath = null;
                                  _selectedFileName = null;
                                }),
                                child: const Icon(Icons.close, size: 16, color: AppColors.onSurfaceVariant),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(height: AppSpacing.space2),
                      ],
                      
                      // Textarea
                      Expanded(child: _buildTextarea()),
                      const SizedBox(height: AppSpacing.space4),
                      Text('Location',
                          style: AppTypography.titleLarge.copyWith(
                            fontWeight: FontWeight.w600,
                          )),
                      const SizedBox(height: 8),
                      ComplaintMapWidget(
                        onLocationSelected: (lat, lng) {
                          setState(() {
                            _latitude = lat;
                            _longitude = lng;
                          });
                        },
                      ),
                      const SizedBox(height: 96), // room for bottom bar
                    ],
                  ),
                ),
                // Bottom bar
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
          // Content area
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
                      'File New Grievance',
                      style: AppTypography.headlineMedium.copyWith(color: AppColors.onSurface),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.space6),
                
                Expanded(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Left column: Form
                      Expanded(
                        flex: 6,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('Describe the issue',
                                style: AppTypography.headlineMedium.copyWith(
                                  fontSize: 26,
                                  fontWeight: FontWeight.w600,
                                )),
                            const SizedBox(height: 4),
                            Text(
                              'Be as specific as possible so we can route it correctly.',
                              style: AppTypography.bodyMedium.copyWith(
                                color: AppColors.onSurfaceVariant,
                              ),
                            ),
                            const SizedBox(height: AppSpacing.space4),
                            _buildSuggestionChips(),
                            const SizedBox(height: AppSpacing.space4),
                            if (_selectedFileName != null) ...[
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                                decoration: BoxDecoration(
                                  color: AppColors.primaryContainer.withValues(alpha: 0.3),
                                  borderRadius: BorderRadius.circular(8),
                                  border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    const Icon(Icons.attach_file, size: 16, color: AppColors.primary),
                                    const SizedBox(width: 8),
                                    Flexible(
                                      child: Text(
                                        _selectedFileName!,
                                        style: AppTypography.caption.copyWith(color: AppColors.primary),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    GestureDetector(
                                      onTap: () => setState(() {
                                        _selectedFilePath = null;
                                        _selectedFileName = null;
                                      }),
                                      child: const Icon(Icons.close, size: 16, color: AppColors.onSurfaceVariant),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(height: AppSpacing.space2),
                            ],
                            Expanded(child: _buildTextarea()),
                            const SizedBox(height: AppSpacing.space4),
                            Text('Location',
                                style: AppTypography.titleLarge.copyWith(
                                  fontWeight: FontWeight.w600,
                                )),
                            const SizedBox(height: 8),
                            ComplaintMapWidget(
                              onLocationSelected: (lat, lng) {
                                setState(() {
                                  _latitude = lat;
                                  _longitude = lng;
                                });
                              },
                            ),
                            const SizedBox(height: 96), // room for bottom bar
                          ],
                        ),
                      ),
                      
                      const SizedBox(width: AppSpacing.space6),
                      
                      // Right column: AI Analysis & Guidelines
                      Expanded(
                        flex: 4,
                        child: Column(
                          children: [
                            if (_showAiCard)
                              SlideTransition(
                                position: _aiCardSlide,
                                child: FadeTransition(
                                  opacity: _aiCardFade,
                                  child: _buildAiCard(),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          
          // Bottom bar
          Positioned(
            bottom: 0,
            left: 0,
            right: 0,
            child: Align(
              alignment: Alignment.centerLeft,
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 800), // Max width of left col
                child: Padding(
                  padding: const EdgeInsets.only(left: AppSpacing.space8),
                  child: _buildBottomBar(),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAiCard() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.space2),
      decoration: BoxDecoration(
        color: AppColors.surfaceContainerHigh,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
          ),
        ],
      ),
      child: Stack(
        children: [
          // Iridescent gradient overlay
          Positioned.fill(
            child: Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [
                    AppColors.primaryFixed.withValues(alpha: 0.2),
                    Colors.transparent,
                  ],
                ),
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Icon(Icons.auto_awesome, color: AppColors.primary, size: 20),
              const SizedBox(width: AppSpacing.space2),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'AI ANALYSIS',
                      style: AppTypography.labelSmall.copyWith(
                        color: AppColors.primary,
                        letterSpacing: 1.2,
                      ),
                    ),
                    const SizedBox(height: 4),
                    _analyzing
                        ? Row(
                            children: [
                              const SizedBox(
                                width: 14,
                                height: 14,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: AppColors.primary,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'Analyzing context...',
                                style: AppTypography.bodyMedium.copyWith(
                                  color: AppColors.onSurface,
                                ),
                              ),
                            ],
                          )
                        : Text(
                            _aiCategory,
                            style: AppTypography.bodyMedium.copyWith(
                              color: AppColors.onSurface,
                            ),
                          ),
                    if (!_analyzing && _aiTags.isNotEmpty) ...[
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 6,
                        runSpacing: 4,
                        children: [
                          ..._aiTags.map((tag) => _buildChip(
                                label: tag,
                                bg: AppColors.secondaryContainer,
                                fg: AppColors.onSecondaryContainer,
                              )),
                          _buildChipWithIcon(
                            icon: Icons.check_circle,
                            label: '$_confidence% Match',
                            bg: AppColors.primaryContainer,
                            fg: AppColors.onPrimaryContainer,
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildChip({
    required String label,
    required Color bg,
    required Color fg,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(label,
          style: AppTypography.labelSmall.copyWith(color: fg)),
    );
  }

  Widget _buildChipWithIcon({
    required IconData icon,
    required String label,
    required Color bg,
    required Color fg,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 14, color: fg),
          const SizedBox(width: 4),
          Text(label, style: AppTypography.labelSmall.copyWith(color: fg)),
        ],
      ),
    );
  }

  Widget _buildSuggestionChips() {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: _suggestions.map((s) {
          return Padding(
            padding: const EdgeInsets.only(right: 8),
            child: GestureDetector(
              onTap: () {
                final cur = _textCtrl.text;
                final sep = cur.isNotEmpty && !cur.endsWith(' ') ? ' ' : '';
                _textCtrl.text = '$cur$sep$s ';
                _textCtrl.selection = TextSelection.fromPosition(
                  TextPosition(offset: _textCtrl.text.length),
                );
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                decoration: BoxDecoration(
                  color: AppColors.surfaceContainer,
                  borderRadius: BorderRadius.circular(100),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.04),
                      blurRadius: 4,
                    ),
                  ],
                ),
                child: Text(
                  s,
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.onSurfaceVariant,
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }

  Widget _buildTextarea() {
    return Focus(
      child: Builder(builder: (context) {
        final focused = Focus.of(context).hasFocus;
        return AnimatedContainer(
          duration: AppAnimations.buttonPress,
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: focused ? AppColors.primary : Colors.transparent,
              width: 2,
            ),
            boxShadow: [
              if (!focused)
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
                  controller: _textCtrl,
                  maxLines: null,
                  expands: true,
                  maxLength: 500,
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.onSurface,
                  ),
                  decoration: InputDecoration(
                    hintText: 'Type your complaint here...',
                    hintStyle: AppTypography.bodyMedium.copyWith(
                      color: AppColors.outline,
                    ),
                    counterText: '',
                    contentPadding: const EdgeInsets.all(16),
                    border: InputBorder.none,
                  ),
                ),
              ),
          // Toolbar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.surfaceContainerLow,
              borderRadius: const BorderRadius.vertical(bottom: Radius.circular(12)),
              border: Border(
                top: BorderSide(color: AppColors.surfaceVariant),
              ),
            ),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.attach_file, size: 22),
                  color: AppColors.onSurfaceVariant,
                  onPressed: _pickFile,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                ),
                IconButton(
                  icon: const Icon(Icons.photo_camera, size: 22),
                  color: AppColors.onSurfaceVariant,
                  onPressed: _pickFile,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
                ),
                const Spacer(),
                Text(
                  '${_textCtrl.text.length} / 500',
                  style: AppTypography.caption.copyWith(
                    color: AppColors.outline,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
      }),
    );
  }

  Widget _buildBottomBar() {
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
          color: _canSubmit ? AppColors.primary : AppColors.surfaceContainer,
          borderRadius: BorderRadius.circular(100),
          boxShadow: _canSubmit
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
            onTap: _canSubmit ? () {
              final draft = ComplaintDraft(
                description: _textCtrl.text,
                aiAnalysis: _aiResponse,
                latitude: _latitude,
                longitude: _longitude,
                imagePath: _selectedFilePath,
              );
              context.push('/ai-review', extra: draft);
            } : null,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'Review Summary',
                  style: AppTypography.titleLarge.copyWith(
                    color: _canSubmit
                        ? AppColors.onPrimary
                        : AppColors.onSurfaceVariant,
                  ),
                ),
                const SizedBox(width: 8),
                AnimatedSlide(
                  offset: _canSubmit ? const Offset(0.1, 0) : Offset.zero,
                  duration: AppAnimations.pageTransition,
                  child: Icon(
                    Icons.arrow_forward,
                    color: _canSubmit
                        ? AppColors.onPrimary
                        : AppColors.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
