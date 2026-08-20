import 'dart:io';
import 'package:record/record.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
import '../core/services/stt_service.dart';

/// Service for recording voice complaints and transcribing them via the backend STT API.
class VoiceService {
  final AudioRecorder _recorder = AudioRecorder();
  final SpeechToTextService _sttService = SpeechToTextService();
  String? _currentRecordingPath;

  /// Check if microphone permission is granted.
  Future<bool> hasPermission() async {
    return await _recorder.hasPermission();
  }

  /// Start recording audio to a temp file.
  Future<void> startRecording() async {
    final dir = await getTemporaryDirectory();
    final fileName =
        'voice_complaint_${DateTime.now().millisecondsSinceEpoch}.m4a';
    _currentRecordingPath = p.join(dir.path, fileName);

    await _recorder.start(
      const RecordConfig(
        encoder: AudioEncoder.aacLc,
        bitRate: 128000,
        sampleRate: 44100,
      ),
      path: _currentRecordingPath!,
    );
  }

  /// Stop the current recording and return the audio file.
  Future<File?> stopRecording() async {
    final path = await _recorder.stop();
    if (path == null) return null;
    _currentRecordingPath = null;
    return File(path);
  }

  /// Check if a recording is currently in progress.
  Future<bool> isRecording() async {
    return await _recorder.isRecording();
  }

  /// Transcribe an audio file using the backend STT endpoint.
  /// Returns the transcribed text, or null if transcription fails.
  Future<String?> transcribeAudio(File audioFile,
      {String? languageCode}) async {
    try {
      final response =
          await _sttService.transcribe(audioFile, languageCode: languageCode);
      return response.transcript;
    } catch (_) {
      return null;
    }
  }

  /// Clean up resources.
  void dispose() {
    _recorder.dispose();
  }
}
