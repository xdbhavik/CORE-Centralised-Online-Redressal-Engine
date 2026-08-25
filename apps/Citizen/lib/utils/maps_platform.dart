import 'package:flutter/foundation.dart';
import 'dart:io';

enum MapsPlatform { native, webFallback, unsupported }

MapsPlatform getMapsPlatform() {
  if (kIsWeb) return MapsPlatform.webFallback;
  if (Platform.isAndroid || Platform.isIOS) return MapsPlatform.native;
  if (Platform.isWindows) return MapsPlatform.webFallback;
  return MapsPlatform.unsupported;
}
