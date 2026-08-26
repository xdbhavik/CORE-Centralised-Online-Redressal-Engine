import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../utils/maps_platform.dart';
import '../services/location_service.dart';
import '../theme/app_colors.dart';
import '../theme/app_typography.dart';
import 'core_button.dart';
import 'core_text_field.dart';

class ComplaintMapWidget extends StatefulWidget {
  final double? initialLatitude;
  final double? initialLongitude;
  final bool interactive;
  final Function(double lat, double lng)? onLocationSelected;
  final double height;

  const ComplaintMapWidget({
    super.key,
    this.initialLatitude,
    this.initialLongitude,
    this.interactive = true,
    this.onLocationSelected,
    this.height = 220,
  });

  @override
  State<ComplaintMapWidget> createState() => _ComplaintMapWidgetState();
}

class _ComplaintMapWidgetState extends State<ComplaintMapWidget> {
  late MapsPlatform _platform;
  LatLng? _currentPosition;
  String? _addressText;
  GoogleMapController? _mapController;
  final LocationService _locationService = LocationService();
  
  // For Windows fallback
  final TextEditingController _latController = TextEditingController();
  final TextEditingController _lngController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _platform = getMapsPlatform();
    
    if (widget.initialLatitude != null && widget.initialLongitude != null) {
      _currentPosition = LatLng(widget.initialLatitude!, widget.initialLongitude!);
      _latController.text = widget.initialLatitude.toString();
      _lngController.text = widget.initialLongitude.toString();
      _updateAddressText();
    } else {
      // Default city view (e.g., center of India/Delhi or user's locale)
      _currentPosition = const LatLng(28.6139, 77.2090); 
    }
  }
  
  @override
  void dispose() {
    _latController.dispose();
    _lngController.dispose();
    super.dispose();
  }

  Future<void> _updateAddressText() async {
    if (_currentPosition == null) return;
    
    final address = await _locationService.getAddressFromCoordinates(
      _currentPosition!.latitude, 
      _currentPosition!.longitude
    );
    
    if (mounted) {
      setState(() {
        _addressText = address;
      });
    }
  }

  Future<void> _useCurrentLocation() async {
    final position = await _locationService.getCurrentLocation();
    if (position != null) {
      final newLatLng = LatLng(position.latitude, position.longitude);
      setState(() {
        _currentPosition = newLatLng;
        _latController.text = position.latitude.toString();
        _lngController.text = position.longitude.toString();
      });
      
      _updateAddressText();
      
      if (_platform != MapsPlatform.webFallback && _mapController != null) {
        _mapController!.animateCamera(CameraUpdate.newLatLngZoom(newLatLng, 15));
      }
      
      if (widget.onLocationSelected != null) {
        widget.onLocationSelected!(position.latitude, position.longitude);
      }
    }
  }

  void _onMapTap(LatLng position) {
    if (!widget.interactive) return;
    
    setState(() {
      _currentPosition = position;
    });
    _updateAddressText();
    
    if (widget.onLocationSelected != null) {
      widget.onLocationSelected!(position.latitude, position.longitude);
    }
  }
  
  void _openInBrowser() async {
    if (_currentPosition == null) return;
    final url = Uri.parse('https://www.google.com/maps/search/?api=1&query=${_currentPosition!.latitude},${_currentPosition!.longitude}');
    if (await canLaunchUrl(url)) {
      await launchUrl(url);
    }
  }

  Widget _buildNativeMap() {
    return GoogleMap(
      initialCameraPosition: CameraPosition(
        target: _currentPosition ?? const LatLng(28.6139, 77.2090),
        zoom: 15,
      ),
      onMapCreated: (controller) => _mapController = controller,
      onTap: _onMapTap,
      markers: _currentPosition != null ? {
        Marker(
          markerId: const MarkerId('selected_location'),
          position: _currentPosition!,
        )
      } : {},
      myLocationEnabled: false,
      myLocationButtonEnabled: false,
      zoomControlsEnabled: widget.interactive,
      mapToolbarEnabled: false,
      scrollGesturesEnabled: widget.interactive,
      zoomGesturesEnabled: widget.interactive,
    );
  }

  Widget _buildFallbackMap() {
    // Windows/Web static map fallback
    // Note: requires a valid API key to render the static map image
    final String apiKey = const String.fromEnvironment('MAPS_API_KEY', defaultValue: '');
    
    Widget mapImage = Container(
      color: AppColors.surfaceContainerHigh,
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.map, size: 48, color: AppColors.outlineVariant),
            const SizedBox(height: 8),
            Text(
              _currentPosition != null 
                  ? '${_currentPosition!.latitude.toStringAsFixed(4)}, ${_currentPosition!.longitude.toStringAsFixed(4)}'
                  : 'Map Preview',
              style: AppTypography.labelSmall.copyWith(color: AppColors.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
    
    if (_currentPosition != null && apiKey.isNotEmpty) {
      final url = 'https://maps.googleapis.com/maps/api/staticmap?center=${_currentPosition!.latitude},${_currentPosition!.longitude}&zoom=15&size=600x300&markers=color:red%7C${_currentPosition!.latitude},${_currentPosition!.longitude}&key=$apiKey';
      mapImage = Image.network(
        url,
        fit: BoxFit.cover,
        errorBuilder: (context, error, stackTrace) => mapImage,
      );
    }
    
    if (!widget.interactive) {
      return Stack(
        fit: StackFit.expand,
        children: [
          mapImage,
          Positioned(
            bottom: 8,
            right: 8,
            child: CoreButton(
              label: 'Open in Maps',
              onPressed: _openInBrowser,
              variant: CoreButtonVariant.secondary,
              isFullWidth: false,
              icon: Icons.open_in_new,
            ),
          ),
        ],
      );
    }
    
    return Column(
      children: [
        Expanded(
          child: Stack(
            fit: StackFit.expand,
            children: [
              mapImage,
            ],
          ),
        ),
        Container(
          padding: const EdgeInsets.all(8.0),
          decoration: const BoxDecoration(
            border: Border(top: BorderSide(color: AppColors.outlineVariant)),
          ),
          child: Row(
            children: [
              Expanded(
                child: CoreTextField(
                  label: 'Latitude',
                  controller: _latController,
                  keyboardType: TextInputType.number,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: CoreTextField(
                  label: 'Longitude',
                  controller: _lngController,
                  keyboardType: TextInputType.number,
                ),
              ),
              const SizedBox(width: 8),
              CoreButton(
                label: 'Update',
                onPressed: () {
                  final lat = double.tryParse(_latController.text);
                  final lng = double.tryParse(_lngController.text);
                  if (lat != null && lng != null) {
                    _onMapTap(LatLng(lat, lng));
                  }
                },
                isFullWidth: false,
              ),
            ],
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.outlineVariant),
          ),
          clipBehavior: Clip.antiAlias,
          child: _platform == MapsPlatform.native 
              ? _buildNativeMap() 
              : _buildFallbackMap(),
        ),
        
        if (widget.interactive) ...[
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: CoreButton(
                  label: 'Use My Current Location',
                  onPressed: _useCurrentLocation,
                  variant: CoreButtonVariant.secondary,
                  icon: Icons.my_location,
                ),
              ),
            ],
          ),
        ],
        
        if (_currentPosition != null) ...[
          const SizedBox(height: 8),
          Row(
            children: [
              const Icon(Icons.location_on, size: 16, color: AppColors.primary),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  _addressText ?? '${_currentPosition!.latitude.toStringAsFixed(6)}, ${_currentPosition!.longitude.toStringAsFixed(6)}',
                  style: AppTypography.bodyMedium.copyWith(color: AppColors.onSurfaceVariant),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ]
      ],
    );
  }
}
