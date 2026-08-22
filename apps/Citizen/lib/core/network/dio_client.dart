import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Global navigator key used by the Dio interceptor to redirect to /login on auth failure.
final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

class DioClient {
  static final DioClient _instance = DioClient._internal();
  factory DioClient() => _instance;
  
  late Dio dio;

  DioClient._internal() {
    // Hosted backend (all platforms). For local development, override at
    // build/run time:
    //   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
    String getBaseUrl() {
      const defined = String.fromEnvironment('API_BASE_URL');
      if (defined.isNotEmpty) return defined;
      return 'https://demo.yogeshghule.me/api/v1';
    }

    dio = Dio(BaseOptions(
      baseUrl: getBaseUrl(),
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 10),
      headers: {
        'Content-Type': 'application/json',
      },
    ));

    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        final prefs = await SharedPreferences.getInstance();
        final token = prefs.getString('access_token');
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        return handler.next(options);
      },
      onError: (DioException e, handler) async {
        // Handle 401 Unauthorized — attempt token refresh
        if (e.response?.statusCode == 401) {
          try {
            final prefs = await SharedPreferences.getInstance();
            final refreshToken = prefs.getString('refresh_token');

            if (refreshToken != null && refreshToken.isNotEmpty) {
              // Call refresh endpoint with a fresh Dio instance to avoid interceptor loop
              final refreshDio = Dio(BaseOptions(
                baseUrl: dio.options.baseUrl,
                connectTimeout: const Duration(seconds: 10),
                receiveTimeout: const Duration(seconds: 10),
              ));

              final refreshResponse = await refreshDio.post(
                '/auth/refresh-token',
                queryParameters: {'refreshToken': refreshToken},
              );

              if (refreshResponse.statusCode == 200) {
                final data = refreshResponse.data;
                await prefs.setString('access_token', data['accessToken'] ?? '');
                await prefs.setString('refresh_token', data['refreshToken'] ?? '');

                // Retry the original request with the new token
                final options = e.requestOptions;
                options.headers['Authorization'] = 'Bearer ${data['accessToken']}';
                final retryResponse = await dio.fetch(options);
                return handler.resolve(retryResponse);
              }
            }
          } catch (_) {
            // Refresh failed — clear tokens and let the error propagate
          }

          // If we reach here, refresh failed. Clear session.
          final prefs = await SharedPreferences.getInstance();
          await prefs.clear();
        }

        return handler.next(e);
      },
    ));
  }

  /// Base URL of the server WITHOUT the `/api/v1` suffix.
  /// Media files are served at /media/{complaintId}/{filename}, not under /api/v1.
  String get serverBaseUrl {
    return dio.options.baseUrl.replaceFirst(RegExp(r'/api/v1/?$'), '');
  }

  /// Converts a backend media path (e.g. "/media/8/photo.jpg") into an
  /// absolute URL. Already-absolute URLs are returned unchanged.
  String resolveMediaUrl(String path) {
    if (path.startsWith('http://') || path.startsWith('https://')) return path;
    final p = path.startsWith('/') ? path : '/$path';
    return '$serverBaseUrl$p';
  }
}
