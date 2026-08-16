import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Typography scale extracted from Cognitive Trust Design System.
/// All styles use the Inter typeface.
class AppTypography {
  AppTypography._();

  /// Display Large — 56px, weight 700, letter-spacing -0.02em
  static TextStyle get displayLarge => GoogleFonts.inter(
        fontSize: 56,
        fontWeight: FontWeight.w700,
        height: 64 / 56,
        letterSpacing: -0.02 * 56,
      );

  /// Display Large (Mobile) — 40px, weight 700, letter-spacing -0.02em
  static TextStyle get displayLargeMobile => GoogleFonts.inter(
        fontSize: 40,
        fontWeight: FontWeight.w700,
        height: 48 / 40,
        letterSpacing: -0.02 * 40,
      );

  /// Headline Medium — 32px, weight 600, letter-spacing -0.01em
  static TextStyle get headlineMedium => GoogleFonts.inter(
        fontSize: 32,
        fontWeight: FontWeight.w600,
        height: 40 / 32,
        letterSpacing: -0.01 * 32,
      );

  /// Title Large — 20px, weight 600
  static TextStyle get titleLarge => GoogleFonts.inter(
        fontSize: 20,
        fontWeight: FontWeight.w600,
        height: 28 / 20,
      );

  /// Body Medium — 16px, weight 400
  static TextStyle get bodyMedium => GoogleFonts.inter(
        fontSize: 16,
        fontWeight: FontWeight.w400,
        height: 24 / 16,
      );

  /// Label Small — 12px, weight 500, letter-spacing 0.05em
  static TextStyle get labelSmall => GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w500,
        height: 16 / 12,
        letterSpacing: 0.05 * 12,
      );

  /// Caption — 12px, weight 400
  static TextStyle get caption => GoogleFonts.inter(
        fontSize: 12,
        fontWeight: FontWeight.w400,
        height: 16 / 12,
      );

  /// Builds the full [TextTheme] for use in ThemeData.
  static TextTheme get textTheme => TextTheme(
        displayLarge: displayLarge,
        displayMedium: displayLargeMobile,
        headlineMedium: headlineMedium,
        titleLarge: titleLarge,
        bodyMedium: bodyMedium,
        bodyLarge: bodyMedium.copyWith(fontSize: 18, height: 28 / 18),
        labelSmall: labelSmall,
        labelMedium: GoogleFonts.inter(
          fontSize: 14,
          fontWeight: FontWeight.w500,
          height: 20 / 14,
        ),
        bodySmall: caption,
      );
}
