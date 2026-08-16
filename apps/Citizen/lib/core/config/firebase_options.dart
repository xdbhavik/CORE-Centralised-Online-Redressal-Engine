import 'package:firebase_core/firebase_core.dart' show FirebaseOptions;
import 'package:flutter/foundation.dart' show kIsWeb;

/// Firebase platform configuration.
///
/// ANDROID  — no code config needed; `google-services.json` in android/app/
///            is auto-read by the google-services Gradle plugin.
/// WEB      — values below are for Firebase project `ai-based-graviance-system`.
class DefaultFirebaseOptions {
  DefaultFirebaseOptions._();

  static FirebaseOptions get currentPlatform {
    if (kIsWeb) {
      return web;
    }
    // Android (and others) read google-services.json natively.
    throw UnsupportedError(
      'DefaultFirebaseOptions.currentPlatform is only configured for web. '
      'On Android call Firebase.initializeApp() without options.',
    );
  }

  static const FirebaseOptions web = FirebaseOptions(
    apiKey: 'AIzaSyAC6tNgKCjFlxAzTTbGquSXmiQDIKgvfik',
    authDomain: 'ai-based-graviance-system.firebaseapp.com',
    projectId: 'ai-based-graviance-system',
    storageBucket: 'ai-based-graviance-system.firebasestorage.app',
    messagingSenderId: '76133383483',
    appId: '1:76133383483:web:d20b86497bc955d0ac4b1e',
    measurementId: 'G-WD5C7N0Q0T',
  );
}
