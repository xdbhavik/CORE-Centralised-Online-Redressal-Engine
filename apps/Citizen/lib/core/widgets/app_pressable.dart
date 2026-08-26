import 'package:flutter/services.dart';
import 'package:flutter/material.dart';

import '../theme/app_animations.dart';

/// Shared pressable wrapper — scales the child down on press and fires a
/// light haptic, giving the whole app a consistent, professional tap feel.
class AppPressable extends StatefulWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final double pressedScale;
  final bool enableHaptics;

  const AppPressable({
    super.key,
    required this.child,
    this.onPressed,
    this.pressedScale = 0.97,
    this.enableHaptics = true,
  });

  @override
  State<AppPressable> createState() => _AppPressableState();
}

class _AppPressableState extends State<AppPressable>
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
    _scale = Tween<double>(begin: 1.0, end: widget.pressedScale).animate(
      CurvedAnimation(parent: _ctrl, curve: AppAnimations.buttonPressCurve),
    );
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  void _handleTapUp() {
    _ctrl.reverse();
    if (widget.onPressed != null) {
      if (widget.enableHaptics) HapticFeedback.lightImpact();
      widget.onPressed!.call();
    }
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: widget.onPressed == null ? null : (_) => _ctrl.forward(),
      onTapUp: widget.onPressed == null ? null : (_) => _handleTapUp(),
      onTapCancel: () => _ctrl.reverse(),
      child: ScaleTransition(scale: _scale, child: widget.child),
    );
  }
}
