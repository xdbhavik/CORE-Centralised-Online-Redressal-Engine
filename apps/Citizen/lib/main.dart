import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import 'core/config/app_config.dart';
import 'core/config/firebase_options.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Try real Firebase Phone OTP. If the native config is missing (e.g.
  // google-services.json not added to android/app/ yet), gracefully fall back
  // to mock mode instead of crashing the app.
  //  - Android auto-reads google-services.json (google-services Gradle plugin)
  //  - Web uses the explicit FirebaseOptions from firebase_options.dart
  var firebaseReady = false;
  try {
    if (kIsWeb) {
      await Firebase.initializeApp(
          options: DefaultFirebaseOptions.currentPlatform);
    } else {
      await Firebase.initializeApp();
    }
    firebaseReady = true;
  } catch (e) {
    debugPrint(
        'Firebase init failed — falling back to mock OTP mode. Add google-services.json for real OTP. Error: $e');
  }
  AppConfig.useFirebaseMock = !firebaseReady;

  runApp(const CoreApp());
}

class CoreApp extends StatelessWidget {
  const CoreApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'CORE Grievance System',
      theme: AppTheme.light,
      routerConfig: appRouter,
      debugShowCheckedModeBanner: false,
    );
  }
}
