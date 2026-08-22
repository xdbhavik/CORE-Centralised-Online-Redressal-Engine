import 'dart:io';
import 'package:dio/dio.dart';
import '../network/dio_client.dart';

class SpeechToTextResponse {
  final String? transcript;
  final String? languageCode;

  SpeechToTextResponse({this.transcript, this.languageCode});

  factory SpeechToTextResponse.fromJson(Map<String, dynamic> json) {
    return SpeechToTextResponse(
      transcript: json['transcript'] as String?,
      languageCode: json['languageCode'] as String?,
    );
  }
}

class SpeechToTextService {
  final Dio _dio = DioClient().dio;

  Future<SpeechToTextResponse> transcribe(File audioFile, {String? languageCode}) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(audioFile.path),
      'languageCode': ?languageCode,
    });

    // NOTE: no trailing slash — '/speech-to-text/' returns 401 from the backend
    // (security matcher permits only the exact '/api/v1/speech-to-text' path).
    final response = await _dio.post(
      '/speech-to-text',
      data: formData,
      options: Options(contentType: 'multipart/form-data'),
    );
    return SpeechToTextResponse.fromJson(response.data);
  }
}
