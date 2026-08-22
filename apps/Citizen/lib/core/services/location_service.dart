import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:geolocator/geolocator.dart';
import 'package:geocoding/geocoding.dart';

class LocationService {
  // Default fallback location (New Delhi) used on Windows where GPS is unavailable
  static const double _fallbackLat = 28.6139;
  static const double _fallbackLng = 77.2090;

  /// Returns the current device location.
  /// On Windows desktop (non-web), returns a fallback coordinate since
  /// [geolocator] does not support Windows GPS.
  Future<Position?> getCurrentLocation() async {
    // Windows fallback
    if (!kIsWeb && _isWindows()) {
      return Position(
        latitude: _fallbackLat,
        longitude: _fallbackLng,
        timestamp: DateTime.now(),
        accuracy: 0,
        altitude: 0,
        heading: 0,
        speed: 0,
        speedAccuracy: 0,
        altitudeAccuracy: 0,
        headingAccuracy: 0,
      );
    }

    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return null;

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) return null;
    }
    if (permission == LocationPermission.deniedForever) return null;

    return await Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
      ),
    );
  }

  bool _isWindows() {
    try {
      return Platform.isWindows;
    } catch (_) {
      return false;
    }
  }

  // Reverse geocode coordinates to a human-readable address
  Future<String?> getAddressFromCoordinates(double lat, double lng) async {
    // Skip reverse geocoding on Windows (not supported)
    if (!kIsWeb && _isWindows()) return null;
    try {
      List<Placemark> placemarks = await placemarkFromCoordinates(lat, lng);
      if (placemarks.isNotEmpty) {
        Placemark place = placemarks.first;
        final components = <String>[];

        if (place.subLocality != null && place.subLocality!.isNotEmpty) {
          components.add(place.subLocality!);
        }
        if (place.locality != null && place.locality!.isNotEmpty) {
          components.add(place.locality!);
        }
        if (place.administrativeArea != null &&
            place.administrativeArea!.isNotEmpty) {
          components.add(place.administrativeArea!);
        }

        if (components.isNotEmpty) {
          return components.join(', ');
        }
        return place.street;
      }
    } catch (_) {
      // Return null if reverse geocoding fails
    }
    return null;
  }
}
