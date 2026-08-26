import 'package:flutter/material.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import 'core_card.dart';

/// A card for displaying key performance indicators with animated counters.
class KpiCard extends StatelessWidget {
  const KpiCard({
    super.key,
    required this.title,
    required this.value,
    this.valuePrefix = '',
    this.valueSuffix = '',
    this.trend,
    this.trendIsPositive = true,
    this.icon,
    this.iconColor,
  });

  final String title;
  final double value;
  final String valuePrefix;
  final String valueSuffix;
  final double? trend;
  final bool trendIsPositive;
  final IconData? icon;
  final Color? iconColor;

  @override
  Widget build(BuildContext context) {
    return CoreCard(
      elevation: 1,
      isHoverable: true,
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  title.toUpperCase(),
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.outline,
                    letterSpacing: 1.2,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (icon != null)
                Icon(
                  icon,
                  size: 20,
                  color: iconColor ?? AppColors.outline,
                ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              TweenAnimationBuilder<double>(
                tween: Tween<double>(begin: 0, end: value),
                duration: AppAnimations.counterAnim,
                curve: AppAnimations.counterCurve,
                builder: (context, val, child) {
                  // Format value (e.g., no decimals if it's a whole number)
                  final String displayValue = val == val.toInt()
                      ? val.toInt().toString()
                      : val.toStringAsFixed(1);
                  return Text(
                    '$valuePrefix$displayValue$valueSuffix',
                    style: AppTypography.displayLarge.copyWith(
                      color: AppColors.onSurface,
                      height: 1.0,
                    ),
                  );
                },
              ),
              if (trend != null) ...[
                const SizedBox(width: 12),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  margin: const EdgeInsets.only(bottom: 6),
                  decoration: BoxDecoration(
                    color: trendIsPositive 
                        ? Colors.green.withValues(alpha: 0.1) 
                        : AppColors.errorContainer.withValues(alpha: 0.5),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        trendIsPositive ? Icons.arrow_upward : Icons.arrow_downward,
                        size: 14,
                        color: trendIsPositive ? Colors.green[700] : AppColors.error,
                      ),
                      const SizedBox(width: 2),
                      Text(
                        '${trend!.toStringAsFixed(1)}%',
                        style: AppTypography.labelSmall.copyWith(
                          color: trendIsPositive ? Colors.green[700] : AppColors.error,
                        ),
                      ),
                    ],
                  ),
                ),
              ]
            ],
          ),
        ],
      ),
    );
  }
}
