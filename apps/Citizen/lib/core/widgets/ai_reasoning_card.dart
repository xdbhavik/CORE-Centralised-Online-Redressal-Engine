import 'package:flutter/material.dart';

import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import 'core_button.dart';
import 'core_card.dart';

/// Glassmorphic AI Reasoning Card pattern from Stitch designs.
class AiReasoningCard extends StatelessWidget {
  const AiReasoningCard({
    super.key,
    required this.confidenceScore,
    required this.title,
    required this.description,
    this.bullets = const [],
    this.primaryActionLabel,
    this.onPrimaryAction,
    this.secondaryActionLabel,
    this.onSecondaryAction,
    this.isThinking = false,
  });

  final int confidenceScore;
  final String title;
  final String description;
  final List<String> bullets;
  final String? primaryActionLabel;
  final VoidCallback? onPrimaryAction;
  final String? secondaryActionLabel;
  final VoidCallback? onSecondaryAction;
  final bool isThinking;

  @override
  Widget build(BuildContext context) {
    if (isThinking) {
      return CoreCard(
        elevation: 3, // Glassmorphic
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Stack(
              alignment: Alignment.center,
              children: [
                // Pulse ring
                TweenAnimationBuilder<double>(
                  tween: Tween(begin: 0.0, end: 1.0),
                  duration: const Duration(milliseconds: 1500),
                  curve: Curves.easeInOut,
                  builder: (context, val, child) {
                    return Transform.scale(
                      scale: 1.0 + (val * 0.5),
                      child: Opacity(
                        opacity: 1.0 - val,
                        child: Container(
                          width: 48,
                          height: 48,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: AppColors.primary.withValues(alpha: 0.2),
                          ),
                        ),
                      ),
                    );
                  },
                ),
                Container(
                  width: 40,
                  height: 40,
                  decoration: const BoxDecoration(
                    color: AppColors.primary,
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(
                    Icons.auto_awesome,
                    color: AppColors.onPrimary,
                    size: 20,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              'Analyzing context...',
              style: AppTypography.labelSmall.copyWith(
                color: AppColors.onSurfaceVariant,
              ),
            ),
          ],
        ),
      );
    }

    return CoreCard(
      elevation: 3, // Glassmorphic
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  const Icon(Icons.auto_awesome, color: AppColors.primary, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    'AI Analysis Complete',
                    style: AppTypography.labelSmall.copyWith(
                      color: AppColors.onSurface,
                      letterSpacing: 1.2,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.primaryContainer,
                  borderRadius: BorderRadius.circular(9999),
                ),
                child: Text(
                  '$confidenceScore% Confidence',
                  style: AppTypography.labelSmall.copyWith(
                    color: AppColors.onPrimaryContainer,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            title,
            style: AppTypography.titleLarge.copyWith(color: AppColors.onSurface),
          ),
          const SizedBox(height: 12),
          Text(
            description,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
          ),
          if (bullets.isNotEmpty) ...[
            const SizedBox(height: 12),
            ...bullets.map((bullet) => Padding(
                  padding: const EdgeInsets.only(bottom: 8.0),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Icon(Icons.check_circle, size: 16, color: AppColors.outline),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          bullet,
                          style: AppTypography.bodyMedium.copyWith(
                            color: AppColors.onSurfaceVariant,
                          ),
                        ),
                      ),
                    ],
                  ),
                )),
          ],
          if (primaryActionLabel != null || secondaryActionLabel != null) ...[
            const SizedBox(height: 20),
            Row(
              children: [
                if (primaryActionLabel != null)
                  CoreButton(
                    label: primaryActionLabel!,
                    onPressed: onPrimaryAction,
                    isFullWidth: false,
                    variant: CoreButtonVariant.primary,
                  ),
                if (secondaryActionLabel != null) ...[
                  const SizedBox(width: 12),
                  CoreButton(
                    label: secondaryActionLabel!,
                    onPressed: onSecondaryAction,
                    isFullWidth: false,
                    variant: CoreButtonVariant.text,
                  ),
                ],
              ],
            ),
          ],
        ],
      ),
    );
  }
}
