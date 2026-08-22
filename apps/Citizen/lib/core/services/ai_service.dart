import 'package:dio/dio.dart';
import '../network/dio_client.dart';
import '../models/complaint.dart';

/// Service for all AI module API operations.
/// All AI endpoints are public (no auth required) per the backend.
class AiService {
  final Dio _dio = DioClient().dio;

  /// Preview AI analysis on raw complaint text (before submission).
  Future<AIResponse> analyzePreview({
    required String description,
    String? title,
    double? latitude,
    double? longitude,
    String? address,
    String? language,
  }) async {
    final response = await _dio.post('/ai/analyze', data: {
      'title': ?title,
      'description': description,
      'latitude': ?latitude,
      'longitude': ?longitude,
      'address': ?address,
      'language': ?language,
    });
    return AIResponse.fromJson(response.data);
  }

  /// Run AI analysis on an existing complaint by ID.
  Future<AIResponse> analyzeById(int complaintId) async {
    final response = await _dio.post('/ai/analyze/$complaintId');
    return AIResponse.fromJson(response.data);
  }

  /// Check if a complaint is a potential duplicate.
  Future<DuplicateCheckResponse> checkDuplicate({
    required String description,
    double? latitude,
    double? longitude,
  }) async {
    final response = await _dio.post('/ai/check-duplicate', data: {
      'description': description,
      'latitude': ?latitude,
      'longitude': ?longitude,
    });
    return DuplicateCheckResponse.fromJson(response.data);
  }

  /// AI chatbot for citizens (FAQ / complaint guidance).
  Future<ChatResponse> chat(String message) async {
    final response = await _dio.post('/ai/chat', data: {
      'message': message,
    });
    return ChatResponse.fromJson(response.data);
  }

  /// Translate text between languages.
  Future<TranslationResponse> translate({
    required String text,
    required String sourceLanguage,
    required String targetLanguage,
  }) async {
    final response = await _dio.post('/ai/translate', data: {
      'text': text,
      'sourceLanguage': sourceLanguage,
      'targetLanguage': targetLanguage,
    });
    return TranslationResponse.fromJson(response.data);
  }

  /// Summarize any text.
  Future<String> summarize(String text) async {
    final response = await _dio.post('/ai/summarize', data: {
      'text': text,
    });
    return (response.data['summary'] as String?) ?? '';
  }
}
