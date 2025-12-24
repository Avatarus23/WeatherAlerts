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
  // If your gateway uses `.withSockJS()` -> true, otherwise false.
  final bool useSockJS = true;

  // Gateway base (Flutter Web runs in browser, so "localhost" means your PC)
  final String gatewayHttpBase = 'http://localhost:8081';
  final String gatewayWsBase = 'ws://localhost:8081';

  StompClient? _stomp;
  bool _connected = false;

  // ----------------------------
  // Metric selection
  // ----------------------------
  final List<String> _metrics = const [
    'pm10',
    'pm25',
    'temperature',
    'humidity',
    'pressure',
    'noise_dba',
  ];

  String _selectedMetric = 'pm10';

  // metric -> (areaKey -> level)
  final Map<String, Map<String, String>> _levelByAreaByMetric = {};

  // GeoJSON polygons (areaKey -> polygon points)
  final Map<String, List<LatLng>> _areaPolygons = {};
  bool _geoLoaded = false;

  @override
  void initState() {
    super.initState();

    // init metric maps
    for (final m in _metrics) {
      _levelByAreaByMetric[m] = {};
    }

    _loadGeoJson();
    _connectStomp();
  }

  @override
  void dispose() {
    _stomp?.deactivate();
    super.dispose();
  }

  // ----------------------------
  // KEY NORMALIZATION (GeoJSON -> backend key)
  // ----------------------------
  String _canonicalAreaKey(String input) {
    var s = input.trim();

    // GeoJSON uses "Општина X"
    s = s.replaceAll('Општина', '').trim();

    // normalize spaces
    s = s.replaceAll(RegExp(r'\s+'), ' ');

    // Map Cyrillic municipality names -> backend keys
    const map = {
      'Аеродром': 'aerodrom',
      'Бутел': 'butel',
      'Гази Баба': 'gazi_baba',
      'Ѓорче Петров': 'gjorce_petrov',
      'Карпош': 'karposh',
      'Кисела Вода': 'kisela_voda',
      'Сарај': 'saraj',
      'Центар': 'centar',
      'Чаир': 'cair',
      'Шуто Оризари': 'suto_orizari',
    };

    if (map.containsKey(s)) return map[s]!;

    final lower = s.toLowerCase();
    if (map.values.contains(lower)) return lower;

    return lower.replaceAll(' ', '_');
  }

  // ----------------------------
  // STOMP
  // ----------------------------
  void _connectStomp() {
    final String wsEndpoint =
        useSockJS ? '$gatewayHttpBase/ws' : '$gatewayWsBase/ws';

    _stomp = StompClient(
      config: useSockJS
          ? StompConfig.sockJS(
              url: wsEndpoint,
              onConnect: _onConnect,
              onStompError: (f) => _log('STOMP error: ${f.body}'),
              onWebSocketError: (e) => _log('WS error: $e'),
              onDisconnect: (_) => _setConnected(false),
              heartbeatIncoming: const Duration(seconds: 10),
              heartbeatOutgoing: const Duration(seconds: 10),
            )
          : StompConfig(
              url: wsEndpoint,
              onConnect: _onConnect,
              onStompError: (f) => _log('STOMP error: ${f.body}'),
              onWebSocketError: (e) => _log('WS error: $e'),
              onDisconnect: (_) => _setConnected(false),
              heartbeatIncoming: const Duration(seconds: 10),
              heartbeatOutgoing: const Duration(seconds: 10),
            ),
    );

    _stomp!.activate();
  }

  void _onConnect(StompFrame frame) {
    _setConnected(true);
    _log('STOMP connected');

    const areas = [
      'aerodrom',
      'butel',
      'gazi_baba',
      'gjorce_petrov',
      'karposh',
      'kisela_voda',
      'saraj',
      'centar',
      'cair',
      'suto_orizari',
    ];

    for (final area in areas) {
      _stomp!.subscribe(
        destination: '/topic/alerts/$area',
        callback: (frame) {
          _handleAlertMessage(frame.body);
        },
      );
    }

    // Optional debug:
    // _stomp!.subscribe(
    //   destination: '/topic/alerts/all',
    //   callback: (frame) => _log('RAW FRAME (all): ${frame.body}'),
    // );
  }

  void _setConnected(bool value) {
    if (!mounted) {
      _connected = value;
      return;
    }
    setState(() => _connected = value);
  }

  // ----------------------------
  // Alert handling
  // ----------------------------
  void _handleAlertMessage(String? body) {
    if (body == null || body.isEmpty) return;

    try {
      final Map<String, dynamic> jsonMap = jsonDecode(body);

      final String areaRaw = (jsonMap['area'] ?? 'unknown_area').toString();
      final String metric = (jsonMap['metric'] ?? '').toString();
      final String level = (jsonMap['level'] ?? 'GREEN').toString();

      final String areaKey = _canonicalAreaKey(areaRaw);

      // ignore unknowns (optional, but recommended)
      if (areaKey == 'unknown_area') return;

      // If new metric appears (future-proof), accept it and allow dropdown later
      if (!_levelByAreaByMetric.containsKey(metric)) {
        _levelByAreaByMetric[metric] = {};
        if (!_metrics.contains(metric)) {
          // we won't mutate const list; instead just log it
          _log('New metric received (not in dropdown yet): $metric');
        }
      }

      _levelByAreaByMetric[metric]![areaKey] = level;

      // Only redraw if it affects current selected metric
      if (metric == _selectedMetric && mounted) {
        setState(() {});
      }
    } catch (e) {
      _log('Failed to parse alert JSON: $e | body=$body');
    }
  }

  void _log(String s) {
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
      final String text = await rootBundle
          .loadString('assets/geo/skopje_municipalities_admin7.geojson');
      final Map<String, dynamic> geo = jsonDecode(text);

      final List features = (geo['features'] as List?) ?? [];
      final Map<String, List<LatLng>> temp = {};

      for (final f in features) {
        final props = (f['properties'] as Map?) ?? {};
        final geom = (f['geometry'] as Map?) ?? {};
        final type = (geom['type'] ?? '').toString();
        final coords = geom['coordinates'];

        final name = (props['name'] ??
                props['NAME'] ??
                props['municipality'] ??
                props['ADM2_EN'] ??
                props['ADM3_EN'] ??
                'unknown_area')
            .toString();

        final areaKey = _canonicalAreaKey(name);

        // Outer ring only (demo)
        List polyCoords;
        if (type == 'Polygon') {
          polyCoords = (coords as List)[0];
        } else if (type == 'MultiPolygon') {
          polyCoords = (coords as List)[0][0];
        } else {
          continue;
        }

        final points = polyCoords
            .map<LatLng>((c) => LatLng(
                  (c[1] as num).toDouble(),
                  (c[0] as num).toDouble(),
                ))
            .toList();

        if (points.isNotEmpty) {
          temp[areaKey] = points;
          _log('GEO area="$name" -> key="$areaKey"');
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
  String? _levelForAreaSelectedMetric(String areaKey) {
    return _levelByAreaByMetric[_selectedMetric]?[areaKey];
  }

  Color _fillForArea(String areaKey) {
    final level = _levelForAreaSelectedMetric(areaKey);

    if (level == null) {
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
    final level = _levelForAreaSelectedMetric(areaKey);
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

  int _areasWithStatusSelectedMetric() {
    final m = _levelByAreaByMetric[_selectedMetric];
    if (m == null) return 0;
    return m.length;
  }

  @override
  Widget build(BuildContext context) {
    final polygons = _geoLoaded ? _buildPolygons() : <Polygon>[];

    return Scaffold(
      appBar: AppBar(
        title: Text('Skopje Live Alerts Map (${_selectedMetric.toUpperCase()})'),
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

          if (_geoLoaded) PolygonLayer(polygons: polygons),

          // Metric dropdown (top-left)
          Positioned(
            left: 12,
            top: 12,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
              decoration: BoxDecoration(
                color: Colors.white.withOpacity(0.92),
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Colors.black12),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text('Metric: ', style: TextStyle(fontSize: 12)),
                  DropdownButton<String>(
                    value: _selectedMetric,
                    underline: const SizedBox.shrink(),
                    items: _metrics
                        .map((m) => DropdownMenuItem(
                              value: m,
                              child: Text(m, style: const TextStyle(fontSize: 12)),
                            ))
                        .toList(),
                    onChanged: (v) {
                      if (v == null) return;
                      setState(() => _selectedMetric = v);
                    },
                  ),
                ],
              ),
            ),
          ),

          // Legend / stats (top-right)
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
                    Text('Selected metric: $_selectedMetric'),
                    Text('Areas with status: ${_areasWithStatusSelectedMetric()}'),
                    Text('Polygons loaded: ${_areaPolygons.length}'),
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
