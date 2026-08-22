import 'package:dio/dio.dart';
import '../network/dio_client.dart';

class NotificationResponse {
  final int? notificationId;
  final String? title;
  final String? message;
  final String? type;
  final int? complaintId;
  final String? complaintNo;
  final bool? read;
  final String? createdAt;

  NotificationResponse({
    this.notificationId,
    this.title,
    this.message,
    this.type,
    this.complaintId,
    this.complaintNo,
    this.read,
    this.createdAt,
  });

  factory NotificationResponse.fromJson(Map<String, dynamic> json) {
    return NotificationResponse(
      notificationId: json['notificationId'] as int?,
      title: json['title'] as String?,
      message: json['message'] as String?,
      type: json['type'] as String?,
      complaintId: json['complaintId'] as int?,
      complaintNo: json['complaintNo'] as String?,
      read: json['read'] as bool?,
      createdAt: json['createdAt'] as String?,
    );
  }
}

class NotificationService {
  final Dio _dio = DioClient().dio;

  Future<List<NotificationResponse>> getAllNotifications() async {
    final response = await _dio.get('/notifications');
    final list = response.data as List<dynamic>;
    return list.map((e) => NotificationResponse.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Map<String, dynamic>> getUnreadNotifications() async {
    final response = await _dio.get('/notifications/unread');
    return response.data as Map<String, dynamic>;
  }

  Future<NotificationResponse> markAsRead(int notificationId) async {
    final response = await _dio.put('/notifications/$notificationId/read');
    return NotificationResponse.fromJson(response.data);
  }

  Future<void> markAllAsRead() async {
    await _dio.put('/notifications/read-all');
  }
}
