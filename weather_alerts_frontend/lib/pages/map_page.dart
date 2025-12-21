import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

class MapPage extends StatefulWidget {
  const MapPage({super.key});

  @override
  State<MapPage> createState() => _MapPageState();
}

class _MapPageState extends State<MapPage> {
  // --- CHANGE THIS IF NEEDED ---
  // If your gateway uses `registerStompEndpoints().addEndpoint("/ws").withSockJS()`
  // then set useSockJS = true.
  // If it uses only addEndpoint("/ws") (NO withSockJS), set useSockJS = false.
  final bool useSockJS = false;

  // Gateway base (Flutter Web runs in browser, so "localhost" means your PC)
  final String gatewayHttpBase = 'http://localhost:8081';
  final String gatewayWsBase = 'ws://localhost:8081';

  StompClient? _stomp;
  bool _connected = false;

  // areaKey -> "GREEN"/"RED"/...
  final Map<String, String> _currentLevelByArea = {};

  // GeoJSON polygons (areaKey -> polygon points)
  final Map<String, List<LatLng>> _areaPolygons = {};
  bool _geoLoaded = false;

  @override
  void initState() {
    super.initState();
    _loadGeoJson();
    _connectStomp();
  }

  @override
  void dispose() {
    _stomp?.deactivate();
    super.dispose();
  }

  // ----------------------------
  // STOMP
  // ----------------------------
  void _connectStomp() {
    final String wsEndpoint = useSockJS
        ? '$gatewayHttpBase/ws' // SockJS uses http(s) URL
        : '$gatewayWsBase/ws';  // pure websocket uses ws(s) URL

    _stomp = StompClient(
      config: useSockJS
          ? StompConfig.sockJS(
              url: wsEndpoint,
              onConnect: _onConnect,
              onStompError: (f) => _log('STOMP error: ${f.body}'),
              onWebSocketError: (e) => _log('WS error: $e'),
              onDisconnect: (_) => _setConnected(false),
              stompConnectHeaders: const {},
              webSocketConnectHeaders: const {},
              heartbeatIncoming: const Duration(seconds: 10),
              heartbeatOutgoing: const Duration(seconds: 10),
            )
          : StompConfig(
              url: wsEndpoint,
              onConnect: _onConnect,
              onStompError: (f) => _log('STOMP error: ${f.body}'),
              onWebSocketError: (e) => _log('WS error: $e'),
              onDisconnect: (_) => _setConnected(false),
              stompConnectHeaders: const {},
              webSocketConnectHeaders: const {},
              heartbeatIncoming: const Duration(seconds: 10),
              heartbeatOutgoing: const Duration(seconds: 10),
            ),
    );

    _stomp!.activate();
  }

  void _onConnect(StompFrame frame) {
    _setConnected(true);
    _log('STOMP connected');

    // 1) easiest demo: subscribe to ALL alerts
    _stomp!.subscribe(
      destination: '/topic/alerts/all',
      callback: (msg) => _handleAlertMessage(msg.body),
    );

    // 2) optional: if you want per-area topics, you can subscribe dynamically later too
    // Example:
    // _stomp!.subscribe(destination: '/topic/alerts/centar', callback: (msg) => _handleAlertMessage(msg.body));
  }

  void _setConnected(bool value) {
    if (mounted) {
      setState(() => _connected = value);
    } else {
      _connected = value;
    }
  }

  void _handleAlertMessage(String? body) {
    if (body == null || body.isEmpty) return;

    try {
      final Map<String, dynamic> jsonMap = jsonDecode(body);

      // Gateway forwards AlertMessage with fields like: area, level, avg, metric...
      final String areaRaw = (jsonMap['area'] ?? 'unknown_area').toString();
      final String level = (jsonMap['level'] ?? 'GREEN').toString();

      final String areaKey = areaRaw.toLowerCase().replaceAll(' ', '_');

      _log('ALERT: area=$areaKey level=$level');

      setState(() {
        _currentLevelByArea[areaKey] = level;
      });
    } catch (e) {
      _log('Failed to parse alert JSON: $e | body=$body');
    }
  }

  void _log(String s) {
    // For Flutter Web, this shows in browser DevTools console.
    // Also shows in VS Code debug console sometimes.
    debugPrint(s);
    if (kIsWeb) {
      // ignore: avoid_print
      print(s);
    }
  }

  // ----------------------------
  // GEOJSON
  // ----------------------------
  Future<void> _loadGeoJson() async {
    try {
      final String text =
          await rootBundle.loadString('assets/geo/skopje_municipalities_admin7.geojson');
      final Map<String, dynamic> geo = jsonDecode(text);

      final List features = (geo['features'] as List?) ?? [];

      final Map<String, List<LatLng>> temp = {};

      for (final f in features) {
        final props = (f['properties'] as Map?) ?? {};
        final geom = (f['geometry'] as Map?) ?? {};
        final type = (geom['type'] ?? '').toString();
        final coords = geom['coordinates'];

        // Try common property names
        final name = (props['name'] ??
                props['NAME'] ??
                props['municipality'] ??
                props['ADM2_EN'] ??
                props['ADM3_EN'] ??
                'unknown_area')
            .toString();

        final areaKey = name.toLowerCase().replaceAll(' ', '_');

        // We’ll take the OUTER ring only for demo (works fine visually)
        List polyCoords;

        if (type == 'Polygon') {
          // Polygon: [ [ [lon,lat], ... ] , [hole] ... ]
          polyCoords = (coords as List)[0];
        } else if (type == 'MultiPolygon') {
          // MultiPolygon: [ [ [ [lon,lat],... ] ] , ... ]
          polyCoords = (coords as List)[0][0];
        } else {
          continue;
        }

        final points = polyCoords
            .map<LatLng>((c) => LatLng((c[1] as num).toDouble(), (c[0] as num).toDouble()))
            .toList();

        if (points.isNotEmpty) {
          temp[areaKey] = points;
        }
      }

      setState(() {
        _areaPolygons
          ..clear()
          ..addAll(temp);
        _geoLoaded = true;
      });

      _log('GeoJSON loaded. Areas=${_areaPolygons.length}');
    } catch (e) {
      _log('Failed to load GeoJSON: $e');
    }
  }

  // ----------------------------
  // UI helpers
  // ----------------------------
  Color _fillForArea(String areaKey) {
    final level = _currentLevelByArea[areaKey];

    if (level == null) {
      // neutral (still visible!)
      return Colors.blueGrey.withOpacity(0.18);
    }

    switch (level.toUpperCase()) {
      case 'RED':
        return Colors.red.withOpacity(0.35);
      case 'YELLOW':
      case 'ORANGE':
        return Colors.orange.withOpacity(0.35);
      case 'GREEN':
      default:
        return Colors.green.withOpacity(0.30);
    }
  }

  Color _borderForArea(String areaKey) {
    final level = _currentLevelByArea[areaKey];
    if (level == null) return Colors.black.withOpacity(0.75);

    switch (level.toUpperCase()) {
      case 'RED':
        return Colors.red.withOpacity(0.90);
      case 'YELLOW':
      case 'ORANGE':
        return Colors.orange.withOpacity(0.90);
      case 'GREEN':
      default:
        return Colors.green.withOpacity(0.90);
    }
  }

  List<Polygon> _buildPolygons() {
    return _areaPolygons.entries.map((e) {
      final areaKey = e.key;
      final points = e.value;

      return Polygon(
        points: points,
        color: _fillForArea(areaKey),
        borderColor: _borderForArea(areaKey),
        borderStrokeWidth: 2.2,
        isFilled: true,
      );
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final polygons = _geoLoaded ? _buildPolygons() : <Polygon>[];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Skopje Live Alerts Map'),
        actions: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Center(
              child: Row(
                children: [
                  Icon(
                    _connected ? Icons.cloud_done : Icons.cloud_off,
                    color: _connected ? Colors.green : Colors.red,
                  ),
                  const SizedBox(width: 8),
                  Text(_connected ? 'STOMP connected' : 'Disconnected'),
                ],
              ),
            ),
          ),
        ],
      ),
      body: FlutterMap(
        options: const MapOptions(
          initialCenter: LatLng(41.9981, 21.4254),
          initialZoom: 11.0,
        ),
        children: [
          TileLayer(
            urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            userAgentPackageName: 'mk.ukim.finki.weather_alerts_frontend',
          ),

          // Polygons ABOVE tiles (so fill is visible)
          if (_geoLoaded)
            PolygonLayer(
              polygons: polygons,
            ),

          // Small legend / debug overlay
          Positioned(
            right: 12,
            top: 12,
            child: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.92),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.black12),
              ),
              child: DefaultTextStyle(
                style: const TextStyle(fontSize: 12, color: Colors.black87),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Legend', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    _legendRow(Colors.green.withOpacity(0.35), 'GREEN'),
                    _legendRow(Colors.orange.withOpacity(0.35), 'YELLOW/ORANGE'),
                    _legendRow(Colors.red.withOpacity(0.35), 'RED'),
                    _legendRow(Colors.blueGrey.withOpacity(0.18), 'No data yet'),
                    const SizedBox(height: 8),
                    Text('Areas with status: ${_currentLevelByArea.length}'),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _legendRow(Color c, String label) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        children: [
          Container(width: 14, height: 14, color: c),
          const SizedBox(width: 8),
          Text(label),
        ],
      ),
    );
  }
}
