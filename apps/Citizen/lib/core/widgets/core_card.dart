import 'dart:ui';
import 'package:flutter/material.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_radius.dart';
import '../theme/app_shadows.dart';

/// Reusable card with configurable elevation levels (0-3).
/// Level 3 applies a glassmorphic treatment.
class CoreCard extends StatefulWidget {
  const CoreCard({
    super.key,
    required this.child,
    this.elevation = 1,
    this.borderRadius,
    this.padding,
    this.color,
    this.isHoverable = false,
    this.onTap,
  });

  final Widget child;

  /// 0 = flat, 1 = sm, 2 = md, 3 = xl+blur (AI/Modals)
  final int elevation;

  final BorderRadius? borderRadius;
  final EdgeInsetsGeometry? padding;
  final Color? color;
  final bool isHoverable;
  final VoidCallback? onTap;

  @override
  State<CoreCard> createState() => _CoreCardState();
}

class _CoreCardState extends State<CoreCard> {
  bool _isHovered = false;
  bool _isPressed = false;

  List<BoxShadow> get _shadows {
    int effectiveElevation = widget.elevation;
    if (_isHovered && widget.isHoverable) {
      effectiveElevation = (effectiveElevation + 1).clamp(0, 3);
    }
    
    switch (effectiveElevation) {
      case 0:
        return AppShadows.level0;
      case 1:
        return AppShadows.level1;
      case 2:
        return AppShadows.level2;
      case 3:
        return AppShadows.level3;
      default:
        return AppShadows.level1;
    }
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isGlassmorphic = widget.elevation == 3;
    final radius = widget.borderRadius ?? AppRadius.borderRadiusXl;

    Widget cardContent = AnimatedContainer(
      duration: AppAnimations.cardEntrance,
      curve: AppAnimations.cardEntranceCurve,
      padding: widget.padding ?? const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isGlassmorphic
            ? (widget.color ?? colorScheme.surface).withValues(alpha: 0.8)
            : (widget.color ?? colorScheme.surfaceContainerLowest),
        borderRadius: radius,
        boxShadow: _shadows,
        border: isGlassmorphic
            ? Border.all(color: AppColors.primary.withValues(alpha: 0.2))
            : Border.all(color: AppColors.outlineVariant.withValues(alpha: 0.2)),
      ),
      child: widget.child,
    );

    if (isGlassmorphic) {
      cardContent = ClipRRect(
        borderRadius: radius,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
          child: cardContent,
        ),
      );
    }

    if (widget.isHoverable || widget.onTap != null) {
      cardContent = MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        cursor: widget.onTap != null ? SystemMouseCursors.click : SystemMouseCursors.basic,
        child: GestureDetector(
          onTapDown: (_) => setState(() => _isPressed = true),
          onTapUp: (_) {
            setState(() => _isPressed = false);
            widget.onTap?.call();
          },
          onTapCancel: () => setState(() => _isPressed = false),
          child: AnimatedScale(
            scale: _isPressed ? 0.98 : (_isHovered && widget.isHoverable ? 1.02 : 1.0),
            duration: AppAnimations.buttonPress,
            curve: AppAnimations.buttonPressCurve,
            child: cardContent,
          ),
        ),
      );
    }

    return cardContent;
  }
}
