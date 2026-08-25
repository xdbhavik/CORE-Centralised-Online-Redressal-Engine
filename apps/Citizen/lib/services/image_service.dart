import 'dart:io';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import '../utils/platform_utils.dart';

/// Service for capturing images from camera or picking from gallery.
/// Uses image_picker on Android/iOS and file_picker on Windows.
class ImageService {
  final ImagePicker _picker = ImagePicker();

  /// Capture a photo using the device camera.
  /// Throws [UnsupportedError] on Windows where camera is not supported.
  Future<File?> captureFromCamera() async {
    if (!PlatformUtils.supportsCameraCapture) {
      throw UnsupportedError(
        'Camera capture is not supported on this platform.',
      );
    }
    final XFile? photo = await _picker.pickImage(
      source: ImageSource.camera,
      imageQuality: 85,
      maxWidth: 1920,
      maxHeight: 1080,
    );
    if (photo == null) return null;
    return File(photo.path);
  }

  /// Pick an image from the gallery.
  /// Uses image_picker on mobile, file_picker on Windows.
  Future<File?> pickFromGallery() async {
    if (PlatformUtils.isWindows) {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.image,
        allowMultiple: false,
      );
      if (result == null || result.files.isEmpty) return null;
      final path = result.files.single.path;
      if (path == null) return null;
      return File(path);
    } else {
      final XFile? image = await _picker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
        maxWidth: 1920,
        maxHeight: 1080,
      );
      if (image == null) return null;
      return File(image.path);
    }
  }
}
