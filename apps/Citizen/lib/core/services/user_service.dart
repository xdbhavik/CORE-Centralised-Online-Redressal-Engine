import 'package:dio/dio.dart';
import '../network/dio_client.dart';
import '../models/complaint.dart';

class UserService {
  final Dio _dio = DioClient().dio;

  Future<ProfileResponse> getProfile() async {
    final response = await _dio.get('/auth/profile');
    return ProfileResponse.fromJson(response.data);
  }

  Future<ProfileResponse> updateProfile(ProfileResponse profile) async {
    final response = await _dio.put('/auth/profile', data: profile.toJson());
    return ProfileResponse.fromJson(response.data);
  }
}
