import 'package:dio/dio.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../network/dio_client.dart';
import '../models/complaint.dart';

class AuthService {
  final Dio _dio = DioClient().dio;

  /// Mobile + password login. Throws [DioException] on failure so the UI can
  /// surface the backend's actual error message.
  Future<bool> login(String mobile, String password) async {
    final response = await _dio.post('/auth/login', data: {
      'mobile': mobile,
      'password': password,
    });

    if (response.statusCode == 200) {
      await _storeAuthData(response.data);
      return true;
    }
    return false;
  }

  Future<bool> sendOtp(String mobile, {String purpose = "REGISTRATION"}) async {
    try {
      final response = await _dio.post('/otp/send', data: {
        'mobile': mobile,
        'purpose': purpose,
      });
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }

  Future<bool> verifyOtp(String mobile, String otp, {String purpose = "REGISTRATION"}) async {
    try {
      final response = await _dio.post('/otp/verify', data: {
        'mobile': mobile,
        'otp': otp,
        'purpose': purpose,
      });
      return response.statusCode == 200;
    } catch (e) {
      return false;
    }
  }

  /// Register a new user with a Firebase-verified ID token (real OTP mode).
  Future<bool> registerWithFirebase({
    required String idToken,
    required String name,
    required String email,
    required String password,
    required String preferredLanguage,
  }) async {
    try {
      final response = await _dio.post('/auth/firebase/register', data: {
        'idToken': idToken,
        'name': name,
        if (email.isNotEmpty) 'email': email,
        'password': password,
        'preferredLanguage': preferredLanguage,
      });
      if (response.statusCode == 200) {
        await _storeAuthData(response.data);
        return true;
      }
      return false;
    } catch (e) {
      rethrow;
    }
  }

  /// OTP-only login for existing users via Firebase ID token.
  Future<bool> loginWithFirebase(String idToken) async {
    try {
      final response = await _dio.post('/auth/firebase/login', data: {
        'idToken': idToken,
      });
      if (response.statusCode == 200) {
        await _storeAuthData(response.data);
        return true;
      }
      return false;
    } catch (e) {
      rethrow;
    }
  }

  /// Reset password using a Firebase-verified ID token (real OTP mode).
  Future<bool> resetPasswordWithFirebase({
    required String idToken,
    required String newPassword,
  }) async {
    try {
      final response = await _dio.post('/auth/firebase/reset-password', data: {
        'idToken': idToken,
        'newPassword': newPassword,
      });
      return response.statusCode == 200;
    } catch (e) {
      rethrow;
    }
  }

  Future<bool> register(String name, String mobile, String email, String password, String language) async {
    try {
      final response = await _dio.post('/auth/register', data: {
        'name': name,
        'mobile': mobile,
        'email': email,
        'password': password,
        'preferredLanguage': language,
      });

      if (response.statusCode == 200) {
        await _storeAuthData(response.data);
        return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  }

  Future<void> logout() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final refreshToken = prefs.getString('refresh_token');
      await _dio.post('/auth/logout', queryParameters: {
        'refreshToken': ?refreshToken,
      });
      await prefs.clear();
    } catch (e) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
    }
  }

  /// Fetch the authenticated user's profile from the server.
  Future<ProfileResponse> getProfile() async {
    final response = await _dio.get('/auth/profile');
    return ProfileResponse.fromJson(response.data);
  }

  /// Update the authenticated user's profile.
  Future<ProfileResponse> updateProfile(ProfileResponse profile) async {
    final response = await _dio.put('/auth/profile', data: profile.toJson());
    final updated = ProfileResponse.fromJson(response.data);
    // Sync local storage with updated profile data
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('user_name', updated.name);
    await prefs.setString('user_email', updated.email ?? '');
    await prefs.setString('user_language', updated.language);
    return updated;
  }

  /// Attempt to refresh the access token. Returns true if successful.
  Future<bool> refreshToken() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final refreshToken = prefs.getString('refresh_token');
      if (refreshToken == null || refreshToken.isEmpty) return false;

      final response = await _dio.post(
        '/auth/refresh-token',
        queryParameters: {'refreshToken': refreshToken},
      );

      if (response.statusCode == 200) {
        await _storeAuthData(response.data);
        return true;
      }
      return false;
    } catch (e) {
      return false;
    }
  }

  /// Helper: read the user's display name from local storage.
  static Future<String> getUserName() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('user_name') ?? 'Citizen';
  }

  /// Helper: check if the user is logged in.
  static Future<bool> isLoggedIn() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('access_token');
    return token != null && token.isNotEmpty;
  }

  /// Store all auth-related data from a login/register/refresh response.
  Future<void> _storeAuthData(Map<String, dynamic> data) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('access_token', data['accessToken'] ?? '');
    await prefs.setString('refresh_token', data['refreshToken'] ?? '');
    await prefs.setString('user_role', data['role'] ?? '');
    // Store full user info for profile/greeting
    if (data['userId'] != null) {
      await prefs.setInt('user_id', data['userId'] as int);
    }
    if (data['name'] != null) {
      await prefs.setString('user_name', data['name'] as String);
    }
    if (data['mobile'] != null) {
      await prefs.setString('user_mobile', data['mobile'] as String);
    }
    if (data['email'] != null) {
      await prefs.setString('user_email', data['email'] as String);
    }
  }
}
