import 'package:flutter/material.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';

/// Shows an animated toast overlay based on the Stitch designs.
class CoreToast {
  static void show(
    BuildContext context, {
    required String message,
    bool isError = false,
    String? actionLabel,
    VoidCallback? onAction,
  }) {
    final overlay = Overlay.of(context);
    late OverlayEntry overlayEntry;

    overlayEntry = OverlayEntry(
      builder: (context) => _ToastOverlay(
        message: message,
        isError: isError,
        actionLabel: actionLabel,
        onAction: onAction,
        onDismiss: () => overlayEntry.remove(),
      ),
    );

    overlay.insert(overlayEntry);
  }
}

class _ToastOverlay extends StatefulWidget {
  const _ToastOverlay({
    required this.message,
    required this.isError,
    this.actionLabel,
    this.onAction,
    required this.onDismiss,
  });

  final String message;
  final bool isError;
  final String? actionLabel;
  final VoidCallback? onAction;
  final VoidCallback onDismiss;

  @override
  State<_ToastOverlay> createState() => _ToastOverlayState();
}

class _ToastOverlayState extends State<_ToastOverlay>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<Offset> _slideAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: AppAnimations.toastSlide,
    );

    _slideAnimation = Tween<Offset>(
      begin: const Offset(0, 1.5),
      end: Offset.zero,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: AppAnimations.toastCurve,
    ));

    _controller.forward();

    // Auto dismiss
    Future.delayed(const Duration(seconds: 4), () {
      if (mounted) {
        _controller.reverse().then((_) => widget.onDismiss());
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      bottom: 24,
      left: 24,
      right: 24,
      child: SafeArea(
        child: SlideTransition(
          position: _slideAnimation,
          child: Material(
            color: Colors.transparent,
            child: widget.isError ? _buildErrorToast() : _buildSuccessToast(),
          ),
        ),
      ),
    );
  }

  Widget _buildSuccessToast() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.inverseSurface,
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(
            color: Colors.black12,
            blurRadius: 8,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Expanded(
            child: Text(
              widget.message,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.inverseOnSurface,
              ),
            ),
          ),
          if (widget.actionLabel != null) ...[
            const SizedBox(width: 12),
            InkWell(
              onTap: widget.onAction,
              child: Text(
                widget.actionLabel!.toUpperCase(),
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.inversePrimary,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildErrorToast() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: AppColors.errorContainer,
        borderRadius: BorderRadius.circular(8),
        boxShadow: const [
          BoxShadow(
            color: Colors.black12,
            blurRadius: 8,
            offset: Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        children: [
          const Icon(Icons.error_outline, color: AppColors.onErrorContainer, size: 20),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              widget.message,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.onErrorContainer,
              ),
            ),
          ),
          if (widget.actionLabel != null) ...[
            const SizedBox(width: 12),
            InkWell(
              onTap: widget.onAction,
              child: Text(
                widget.actionLabel!.toUpperCase(),
                style: AppTypography.labelSmall.copyWith(
                  color: AppColors.onErrorContainer,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
