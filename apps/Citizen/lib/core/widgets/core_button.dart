import 'package:flutter/material.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../theme/app_radius.dart';
import '../theme/app_shadows.dart';

/// Button variants defined by the Cognitive Trust design system.
enum CoreButtonVariant { primary, secondary, danger, ghost, text, fab, aiProcessing }

/// Reusable animated button matching the Cognitive Trust component library.
class CoreButton extends StatefulWidget {
  const CoreButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = CoreButtonVariant.primary,
    this.icon,
    this.isFullWidth = true,
    this.isLoading = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final CoreButtonVariant variant;
  final IconData? icon;
  final bool isFullWidth;
  final bool isLoading;

  @override
  State<CoreButton> createState() => _CoreButtonState();
}

class _CoreButtonState extends State<CoreButton> with SingleTickerProviderStateMixin {
  bool _isPressed = false;
  bool _isHovered = false;

  late final AnimationController _aiPulseController;

  @override
  void initState() {
    super.initState();
    _aiPulseController = AnimationController(
      vsync: this,
      duration: AppAnimations.waveformPulse,
    );
    if (widget.variant == CoreButtonVariant.aiProcessing || widget.isLoading) {
      _aiPulseController.repeat(reverse: true);
    }
  }

  @override
  void didUpdateWidget(covariant CoreButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((widget.variant == CoreButtonVariant.aiProcessing || widget.isLoading) &&
        !(oldWidget.variant == CoreButtonVariant.aiProcessing || oldWidget.isLoading)) {
      _aiPulseController.repeat(reverse: true);
    } else if (!(widget.variant == CoreButtonVariant.aiProcessing || widget.isLoading) &&
        (oldWidget.variant == CoreButtonVariant.aiProcessing || oldWidget.isLoading)) {
      _aiPulseController.stop();
    }
  }

  @override
  void dispose() {
    _aiPulseController.dispose();
    super.dispose();
  }

  void _handleTapDown(TapDownDetails details) {
    if (widget.onPressed == null || widget.isLoading) return;
    setState(() => _isPressed = true);
  }

  void _handleTapUp(TapUpDetails details) {
    if (widget.onPressed == null || widget.isLoading) return;
    setState(() => _isPressed = false);
    widget.onPressed!();
  }

  void _handleTapCancel() {
    if (widget.onPressed == null || widget.isLoading) return;
    setState(() => _isPressed = false);
  }

  @override
  Widget build(BuildContext context) {
    final isDisabled = widget.onPressed == null;
    final isAI = widget.variant == CoreButtonVariant.aiProcessing || widget.isLoading;

    Color backgroundColor;
    Color foregroundColor;
    List<BoxShadow>? shadows;
    double radius = AppRadius.md;
    EdgeInsets padding = const EdgeInsets.symmetric(horizontal: 24, vertical: 12);
    TextStyle textStyle = AppTypography.labelSmall;
    
    switch (widget.variant) {
      case CoreButtonVariant.primary:
        backgroundColor = isDisabled ? AppColors.surfaceVariant : AppColors.primary;
        foregroundColor = isDisabled ? AppColors.onSurfaceVariant.withValues(alpha: 0.5) : AppColors.onPrimary;
        shadows = isDisabled ? null : (_isHovered ? AppShadows.level2 : AppShadows.level1);
        break;
      case CoreButtonVariant.secondary:
        backgroundColor = isDisabled ? AppColors.surfaceVariant : AppColors.secondaryContainer;
        foregroundColor = isDisabled ? AppColors.onSurfaceVariant.withValues(alpha: 0.5) : AppColors.onSecondaryContainer;
        shadows = null;
        break;
      case CoreButtonVariant.danger:
        backgroundColor = isDisabled ? AppColors.surfaceVariant : AppColors.errorContainer;
        foregroundColor = isDisabled ? AppColors.onSurfaceVariant.withValues(alpha: 0.5) : AppColors.onErrorContainer;
        shadows = null;
        break;
      case CoreButtonVariant.ghost:
      case CoreButtonVariant.text:
        backgroundColor = _isHovered && !isDisabled ? AppColors.primaryContainer : Colors.transparent;
        foregroundColor = isDisabled ? AppColors.onSurfaceVariant.withValues(alpha: 0.5) : AppColors.primary;
        shadows = null;
        break;
      case CoreButtonVariant.fab:
        backgroundColor = AppColors.primary;
        foregroundColor = AppColors.onPrimary;
        shadows = _isHovered ? AppShadows.level3 : AppShadows.level2;
        radius = AppRadius.xl;
        padding = const EdgeInsets.all(16);
        break;
      case CoreButtonVariant.aiProcessing:
        backgroundColor = AppColors.surfaceContainerLowest;
        foregroundColor = AppColors.onSurface;
        shadows = AppShadows.level1;
        break;
    }

    final buttonContent = Row(
      mainAxisSize: widget.isFullWidth && widget.variant != CoreButtonVariant.fab
          ? MainAxisSize.max
          : MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        if (isAI) ...[
          const SizedBox(
            width: 18,
            height: 18,
            child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.primary),
          ),
          const SizedBox(width: 12),
          Text('Synthesizing Context...', style: textStyle.copyWith(color: foregroundColor)),
        ] else ...[
          if (widget.icon != null) ...[
            Icon(widget.icon, size: 18, color: foregroundColor),
            if (widget.variant != CoreButtonVariant.fab) const SizedBox(width: 8),
          ],
          if (widget.variant != CoreButtonVariant.fab)
            Text(widget.label, style: textStyle.copyWith(color: foregroundColor)),
        ],
      ],
    );

    Widget animatedContainer = AnimatedContainer(
      duration: AppAnimations.buttonPress,
      curve: AppAnimations.buttonPressCurve,
      padding: padding,
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(radius),
        boxShadow: shadows,
      ),
      child: isAI
          ? AnimatedBuilder(
              animation: _aiPulseController,
              builder: (context, child) {
                return Stack(
                  alignment: Alignment.center,
                  children: [
                    Container(
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(radius),
                        gradient: LinearGradient(
                          colors: [
                            AppColors.primaryFixed.withValues(alpha: 0.2 * _aiPulseController.value),
                            AppColors.tertiaryFixed.withValues(alpha: 0.3 * _aiPulseController.value),
                            AppColors.primaryFixed.withValues(alpha: 0.2 * _aiPulseController.value),
                          ],
                        ),
                      ),
                    ),
                    buttonContent,
                  ],
                );
              },
            )
          : buttonContent,
    );

    return MouseRegion(
      onEnter: (_) => setState(() => _isHovered = true),
      onExit: (_) => setState(() => _isHovered = false),
      cursor: isDisabled || isAI ? SystemMouseCursors.basic : SystemMouseCursors.click,
      child: GestureDetector(
        onTapDown: _handleTapDown,
        onTapUp: _handleTapUp,
        onTapCancel: _handleTapCancel,
        child: AnimatedScale(
          scale: _isPressed ? 0.96 : 1.0,
          duration: AppAnimations.buttonPress,
          curve: AppAnimations.buttonPressCurve,
          child: animatedContainer,
        ),
      ),
    );
  }
}
