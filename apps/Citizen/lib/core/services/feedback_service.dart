import 'package:dio/dio.dart';
import '../network/dio_client.dart';

class FeedbackResponse {
  final int? id;
  final int? rating;
  final String? comments;
  
  FeedbackResponse({this.id, this.rating, this.comments});
  
  factory FeedbackResponse.fromJson(Map<String, dynamic> json) {
    return FeedbackResponse(
      id: json['id'] as int?,
      rating: json['rating'] as int?,
      comments: json['comments'] as String?,
    );
  }
}

class FeedbackService {
  final Dio _dio = DioClient().dio;

  Future<FeedbackResponse> submitFeedback(int complaintId, int rating, {String? comments}) async {
    final response = await _dio.post(
      '/complaints/$complaintId/feedback',
      data: {
        'rating': rating,
        'comments': ?comments,
      },
    );
    return FeedbackResponse.fromJson(response.data);
  }

  Future<FeedbackResponse> getFeedback(int complaintId) async {
    final response = await _dio.get('/complaints/$complaintId/feedback');
    return FeedbackResponse.fromJson(response.data);
  }
}
