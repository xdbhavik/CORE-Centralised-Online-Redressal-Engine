import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme/app_animations.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import '../theme/app_radius.dart';

/// Reusable input field matching Cognitive Trust forms.
class CoreTextField extends StatefulWidget {
  const CoreTextField({
    super.key,
    this.controller,
    required this.label,
    this.hintText,
    this.isPassword = false,
    this.isEnabled = true,
    this.errorText,
    this.prefixIcon,
    this.suffixIcon,
    this.onSuffixTap,
    this.isVoiceInput = false,
    this.maxLines = 1,
    this.keyboardType,
    this.onChanged,
    this.inputFormatters,
    this.maxLength,
    this.autofillHints,
    this.textInputAction,
    this.onSubmitted,
  });

  final TextEditingController? controller;
  final String label;
  final String? hintText;
  final bool isPassword;
  final bool isEnabled;
  final String? errorText;
  final IconData? prefixIcon;
  final IconData? suffixIcon;
  final VoidCallback? onSuffixTap;
  final bool isVoiceInput;
  final int maxLines;
  final TextInputType? keyboardType;
  final ValueChanged<String>? onChanged;
  final List<TextInputFormatter>? inputFormatters;
  final int? maxLength;
  final Iterable<String>? autofillHints;
  final TextInputAction? textInputAction;
  final ValueChanged<String>? onSubmitted;

  @override
  State<CoreTextField> createState() => _CoreTextFieldState();
}

class _CoreTextFieldState extends State<CoreTextField> {
  final FocusNode _focusNode = FocusNode();
  bool _isFocused = false;
  bool _obscureText = true;

  @override
  void initState() {
    super.initState();
    _obscureText = widget.isPassword;
    _focusNode.addListener(() {
      setState(() {
        _isFocused = _focusNode.hasFocus;
      });
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final hasError = widget.errorText != null;

    Color borderColor = AppColors.outlineVariant;
    if (hasError) {
      borderColor = AppColors.error;
    } else if (_isFocused) {
      borderColor = AppColors.primary;
    }

    Color bgColor = widget.isEnabled 
        ? AppColors.surfaceContainerLowest 
        : AppColors.surfaceVariant;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        AnimatedContainer(
          duration: AppAnimations.buttonPress,
          curve: AppAnimations.pageCurve,
          decoration: BoxDecoration(
            color: bgColor,
            borderRadius: BorderRadius.circular(AppRadius.md),
            border: Border.all(
              color: borderColor,
              width: _isFocused || hasError ? 2 : 1,
            ),
            boxShadow: _isFocused && !hasError
                ? [
                    BoxShadow(
                      color: AppColors.primary.withValues(alpha: 0.1),
                      blurRadius: 8,
                      offset: const Offset(0, 2),
                    )
                  ]
                : null,
          ),
          child: TextFormField(
            controller: widget.controller,
            focusNode: _focusNode,
            obscureText: _obscureText,
            enabled: widget.isEnabled,
            maxLines: widget.maxLines,
            keyboardType: widget.keyboardType,
            onChanged: widget.onChanged,
            inputFormatters: widget.inputFormatters,
            maxLength: widget.maxLength,
            autofillHints: widget.autofillHints,
            textInputAction: widget.textInputAction,
            onFieldSubmitted: widget.onSubmitted,
            style: AppTypography.bodyMedium.copyWith(
              color: widget.isEnabled 
                  ? AppColors.onSurface 
                  : AppColors.onSurfaceVariant.withValues(alpha: 0.5),
            ),
            decoration: InputDecoration(
              labelText: widget.label,
              hintText: widget.hintText,
              counterText: '',
              labelStyle: AppTypography.bodyMedium.copyWith(
                color: hasError 
                    ? AppColors.error 
                    : (_isFocused ? AppColors.primary : AppColors.onSurfaceVariant),
              ),
              floatingLabelStyle: AppTypography.labelSmall.copyWith(
                color: hasError 
                    ? AppColors.error 
                    : (_isFocused ? AppColors.primary : AppColors.onSurfaceVariant),
              ),
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              errorBorder: InputBorder.none,
              focusedErrorBorder: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              prefixIcon: widget.prefixIcon != null
                  ? Icon(widget.prefixIcon, 
                      color: _isFocused ? AppColors.primary : AppColors.outline)
                  : null,
              suffixIcon: widget.isPassword
                  ? IconButton(
                      icon: Icon(
                        _obscureText ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                        color: AppColors.outline,
                        size: 20,
                      ),
                      onPressed: () {
                        setState(() {
                          _obscureText = !_obscureText;
                        });
                      },
                    )
                  : (widget.isVoiceInput
                      ? IconButton(
                          icon: Icon(Icons.mic_none_outlined, 
                              color: _isFocused ? AppColors.primary : AppColors.onSurfaceVariant),
                          onPressed: widget.onSuffixTap,
                        )
                      : (widget.suffixIcon != null
                          ? IconButton(
                              icon: Icon(widget.suffixIcon, color: AppColors.outline),
                              onPressed: widget.onSuffixTap,
                            )
                          : null)),
            ),
          ),
        ),
        if (hasError)
          Padding(
            padding: const EdgeInsets.only(top: 6, left: 16),
            child: Text(
              widget.errorText!,
              style: AppTypography.labelSmall.copyWith(color: AppColors.error),
            ),
          ),
      ],
    );
  }
}
