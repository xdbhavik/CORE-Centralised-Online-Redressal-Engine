import 'dart:io';
import 'package:dio/dio.dart';
import '../network/dio_client.dart';
import '../models/complaint.dart';

/// Service for all citizen complaint API operations.
class ComplaintService {
  final Dio _dio = DioClient().dio;

  /// Create a new complaint.
  /// Returns the [ComplaintResponse] with complaint ID and number.
  Future<ComplaintResponse> createComplaint({
    String? title,
    required String description,
    double? latitude,
    double? longitude,
    String? address,
    String? language,
  }) async {
    final response = await _dio.post('/complaints', data: {
      'title': ?title,
      'description': description,
      'latitude': ?latitude,
      'longitude': ?longitude,
      'address': ?address,
      'language': ?language,
    });
    return ComplaintResponse.fromJson(response.data);
  }

  /// Get all complaints for the logged-in citizen.
  Future<List<ComplaintResponse>> getMyComplaints() async {
    final response = await _dio.get('/complaints/my');
    final list = response.data as List<dynamic>;
    return list.map((e) => ComplaintResponse.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Get full details of a specific complaint (ownership enforced on backend).
  Future<ComplaintDetailsResponse> getComplaintDetails(int id) async {
    final response = await _dio.get('/complaints/$id');
    return ComplaintDetailsResponse.fromJson(response.data);
  }

  /// Update a complaint (blocked if status is RESOLVED or CLOSED).
  Future<ComplaintDetailsResponse> updateComplaint(
    int id, {
    String? title,
    String? description,
    double? latitude,
    double? longitude,
    String? address,
    String? language,
  }) async {
    final response = await _dio.put('/complaints/$id', data: {
      'title': ?title,
      'description': ?description,
      'latitude': ?latitude,
      'longitude': ?longitude,
      'address': ?address,
      'language': ?language,
    });
    return ComplaintDetailsResponse.fromJson(response.data);
  }

  /// Soft-delete a complaint (blocked if RESOLVED or CLOSED).
  Future<void> deleteComplaint(int id) async {
    await _dio.delete('/complaints/$id');
  }

  /// Close a resolved complaint.
  Future<ComplaintDetailsResponse> closeComplaint(int id) async {
    final response = await _dio.post('/complaints/$id/close');
    return ComplaintDetailsResponse.fromJson(response.data);
  }

  /// Reopen a resolved complaint.
  Future<ComplaintDetailsResponse> reopenComplaint(int id, String reason) async {
    final response = await _dio.post(
      '/complaints/$id/reopen',
      data: {'reason': reason},
    );
    return ComplaintDetailsResponse.fromJson(response.data);
  }

  /// Upload a media file (image/video/PDF) for a complaint.
  Future<ComplaintResponse> uploadMedia(int complaintId, File file) async {
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(file.path),
    });
    final response = await _dio.post(
      '/complaints/$complaintId/media',
      data: formData,
      options: Options(contentType: 'multipart/form-data'),
    );
    return ComplaintResponse.fromJson(response.data);
  }

  /// Get the status timeline for a complaint.
  Future<List<TimelineDTO>> getTimeline(int complaintId) async {
    final response = await _dio.get('/complaints/$complaintId/timelineDTOs');
    final list = response.data as List<dynamic>;
    return list.map((e) => TimelineDTO.fromJson(e as Map<String, dynamic>)).toList();
  }
}
