import 'package:flutter/material.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';

enum StatusBadgeType {
  highPriority,
  pending,
  active,
  resolved,
  confidenceHigh,
  confidenceMedium,
  confidenceLow,
}

/// Animated status badge that updates color/icon based on state.
class StatusBadge extends StatelessWidget {
  const StatusBadge({
    super.key,
    required this.type,
    this.label,
  });

  final StatusBadgeType type;
  final String? label;

  @override
  Widget build(BuildContext context) {
    Color bgColor;
    Color textColor;
    Widget? icon;

    switch (type) {
      case StatusBadgeType.highPriority:
        bgColor = AppColors.errorContainer;
        textColor = AppColors.onErrorContainer;
        icon = Container(
          width: 6,
          height: 6,
          decoration: const BoxDecoration(
            color: AppColors.error,
            shape: BoxShape.circle,
          ),
        );
        break;
      case StatusBadgeType.pending:
        bgColor = AppColors.surfaceContainer;
        textColor = AppColors.onSurface;
        break;
      case StatusBadgeType.active:
        bgColor = AppColors.primaryContainer;
        textColor = AppColors.onPrimaryContainer;
        icon = Container(
          width: 6,
          height: 6,
          decoration: const BoxDecoration(
            color: AppColors.primary,
            shape: BoxShape.circle,
          ),
        );
        break;
      case StatusBadgeType.resolved:
        bgColor = Colors.green.withValues(alpha: 0.15);
        textColor = Colors.green[800]!;
        icon = Icon(Icons.check_circle, size: 12, color: Colors.green[700]);
        break;
      case StatusBadgeType.confidenceHigh:
        bgColor = AppColors.primaryContainer;
        textColor = AppColors.onPrimaryContainer;
        break;
      case StatusBadgeType.confidenceMedium:
        bgColor = AppColors.secondaryContainer;
        textColor = AppColors.onSecondaryContainer;
        break;
      case StatusBadgeType.confidenceLow:
        bgColor = AppColors.errorContainer;
        textColor = AppColors.onErrorContainer;
        break;
    }

    return AnimatedContainer(
      duration: AppAnimations.pageTransition,
      curve: AppAnimations.pageCurve,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            icon,
            const SizedBox(width: 6),
          ],
          Text(
            label ?? _defaultLabel(type),
            style: AppTypography.labelSmall.copyWith(
              color: textColor,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }

  String _defaultLabel(StatusBadgeType t) {
    switch (t) {
      case StatusBadgeType.highPriority:
        return 'High Priority';
      case StatusBadgeType.pending:
        return 'Pending';
      case StatusBadgeType.active:
        return 'Active';
      case StatusBadgeType.resolved:
        return 'Resolved';
      case StatusBadgeType.confidenceHigh:
        return '98%';
      case StatusBadgeType.confidenceMedium:
        return '74%';
      case StatusBadgeType.confidenceLow:
        return '42%';
    }
  }
}
