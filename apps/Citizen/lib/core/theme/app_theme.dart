import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'app_typography.dart';
import 'app_radius.dart';

/// Composes all Cognitive Trust design tokens into a Material 3 [ThemeData].
class AppTheme {
  AppTheme._();

  static ThemeData get light => ThemeData(
        useMaterial3: true,
        colorScheme: AppColors.colorScheme,
        textTheme: AppTypography.textTheme,
        scaffoldBackgroundColor: AppColors.surface,
        // Smoother, more modern ink ripple across the app.
        splashFactory: InkSparkle.splashFactory,
        visualDensity: VisualDensity.adaptivePlatformDensity,
        // Consistent rounded dialogs.
        dialogTheme: DialogThemeData(
          backgroundColor: AppColors.surfaceContainerLowest,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.borderRadiusXxl,
          ),
          titleTextStyle: AppTypography.titleLarge.copyWith(
            color: AppColors.onSurface,
          ),
          contentTextStyle: AppTypography.bodyMedium.copyWith(
            color: AppColors.onSurfaceVariant,
          ),
        ),
        // Floating, rounded snackbars feel more professional.
        snackBarTheme: SnackBarThemeData(
          behavior: SnackBarBehavior.floating,
          backgroundColor: AppColors.inverseSurface,
          contentTextStyle: AppTypography.bodyMedium.copyWith(
            color: AppColors.inverseOnSurface,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.borderRadiusLg,
          ),
          insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        ),
        // Rounded top sheets (voice input, pickers, etc.).
        bottomSheetTheme: const BottomSheetThemeData(
          backgroundColor: AppColors.surfaceContainerLowest,
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          ),
          showDragHandle: false,
        ),
        progressIndicatorTheme: const ProgressIndicatorThemeData(
          color: AppColors.primary,
          circularTrackColor: AppColors.surfaceContainerHigh,
        ),
        dividerTheme: DividerThemeData(
          color: AppColors.outlineVariant.withValues(alpha: 0.3),
          thickness: 1,
        ),
        appBarTheme: AppBarTheme(
          backgroundColor: AppColors.surface.withValues(alpha: 0.8),
          foregroundColor: AppColors.onSurface,
          elevation: 0,
          centerTitle: true,
          titleTextStyle: AppTypography.titleLarge.copyWith(
            color: AppColors.onSurface,
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.primary,
            foregroundColor: AppColors.onPrimary,
            minimumSize: const Size(double.infinity, 56),
            shape: const StadiumBorder(),
            textStyle: AppTypography.labelSmall,
            elevation: 2,
          ),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.onSurface,
            backgroundColor: AppColors.surfaceContainer,
            minimumSize: const Size(double.infinity, 56),
            shape: const StadiumBorder(),
            textStyle: AppTypography.labelSmall,
            side: BorderSide.none,
          ),
        ),
        textButtonTheme: TextButtonThemeData(
          style: TextButton.styleFrom(
            foregroundColor: AppColors.primary,
            textStyle: AppTypography.labelSmall,
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.surfaceContainerLowest,
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
          border: OutlineInputBorder(
            borderRadius: AppRadius.borderRadiusFull,
            borderSide: BorderSide(color: AppColors.outlineVariant),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: AppRadius.borderRadiusFull,
            borderSide: BorderSide(color: AppColors.outlineVariant),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: AppRadius.borderRadiusFull,
            borderSide: const BorderSide(color: AppColors.primary, width: 2),
          ),
          hintStyle: AppTypography.bodyMedium.copyWith(
            color: AppColors.outline,
          ),
        ),
        cardTheme: CardThemeData(
          color: AppColors.surfaceContainerLowest,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: AppRadius.borderRadiusXl,
          ),
          margin: EdgeInsets.zero,
        ),
      );
}
