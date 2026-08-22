import 'package:dio/dio.dart';
import '../network/dio_client.dart';

class OtpResponse {
  final bool? success;
  final String? message;
  final int? expiresInMinutes;
  final String? sessionInfo;

  OtpResponse({
    this.success,
    this.message,
    this.expiresInMinutes,
    this.sessionInfo,
  });

  factory OtpResponse.fromJson(Map<String, dynamic> json) {
    return OtpResponse(
      success: json['success'] as bool?,
      message: json['message'] as String?,
      expiresInMinutes: json['expiresInMinutes'] as int?,
      sessionInfo: json['sessionInfo'] as String?,
    );
  }
}

class OtpService {
  final Dio _dio = DioClient().dio;

  Future<OtpResponse> sendOtp(String mobile, String purpose) async {
    final response = await _dio.post(
      '/otp/send',
      data: {
        'mobile': mobile,
        'purpose': purpose, // e.g. "LOGIN", "REGISTER", "PASSWORD_RESET"
      },
    );
    return OtpResponse.fromJson(response.data);
  }

  Future<OtpResponse> verifyOtp(String mobile, String otp, String purpose) async {
    final response = await _dio.post(
      '/otp/verify',
      data: {
        'mobile': mobile,
        'otp': otp,
        'purpose': purpose,
      },
    );
    return OtpResponse.fromJson(response.data);
  }

  /// Reset password after OTP verification.
  /// Backend PasswordResetRequest expects: mobile, otp, newPassword.
  Future<OtpResponse> resetPassword(String mobile, String otp, String newPassword) async {
    final response = await _dio.post(
      '/otp/reset-password',
      data: {
        'mobile': mobile,
        'otp': otp,
        'newPassword': newPassword,
      },
    );
    return OtpResponse.fromJson(response.data);
  }
}
