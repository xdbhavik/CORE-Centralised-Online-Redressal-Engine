import 'package:dio/dio.dart';
import '../network/dio_client.dart';

class NotificationResponse {
  final int id;
  final String title;
  final String message;
  final String type;
  final bool isRead;
  final String createdAt;

  NotificationResponse({
    required this.id,
    required this.title,
    required this.message,
    required this.type,
    required this.isRead,
    required this.createdAt,
  });

  factory NotificationResponse.fromJson(Map<String, dynamic> json) {
    return NotificationResponse(
      id: json['id'] ?? 0,
      title: json['title'] ?? 'Notification',
      message: json['message'] ?? '',
      type: json['type'] ?? 'INFO',
      isRead: json['isRead'] ?? json['read'] ?? false,
      createdAt: json['createdAt'] ?? '',
    );
  }
}

class AlertService {
  final Dio _dio = DioClient().dio;

  Future<List<NotificationResponse>> getNotifications() async {
    final response = await _dio.get('/notifications');
    final list = response.data as List<dynamic>;
    return list.map((e) => NotificationResponse.fromJson(e)).toList();
  }

  Future<Map<String, dynamic>> getUnreadNotifications() async {
    final response = await _dio.get('/notifications/unread');
    return response.data as Map<String, dynamic>;
  }

  Future<NotificationResponse> markAsRead(int id) async {
    final response = await _dio.put('/notifications/$id/read');
    return NotificationResponse.fromJson(response.data);
  }

  Future<void> markAllAsRead() async {
    await _dio.put('/notifications/read-all');
  }
}
