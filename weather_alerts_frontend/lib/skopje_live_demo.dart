import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

class SkopjeMapLivePage extends StatefulWidget {
  const SkopjeMapLivePage({super.key});

  @override
  State<SkopjeMapLivePage> createState() => _SkopjeMapLivePageState();
}

class _SkopjeMapLivePageState extends State<SkopjeMapLivePage> {
  StompClient? _client;

  // live values per area
  final Map<String, double> _pm10ByArea = {
    'centar': double.nan,
    'gazi_baba': double.nan,
    'karpos': double.nan,
    'aerodrom': double.nan,
    'kisela_voda': double.nan,
  };

  // Skopje center
  static const LatLng _skCenter = LatLng(41.9981, 21.4254);

  @override
  void initState() {
    super.initState();
    _connectStomp();
  }

  @override
  void dispose() {
    _client?.deactivate();
    super.dispose();
  }

  void _connectStomp() {
    // Use your gateway port (you currently have 8081)
    // IMPORTANT for Flutter web: use ws:// not http://
    final url = 'ws://localhost:8081/ws';

    _client = StompClient(
      config: StompConfig(
        url: url,
        onConnect: _onConnect,
        onWebSocketError: (dynamic e) => debugPrint('WS error: $e'),
        onStompError: (StompFrame f) => debugPrint('STOMP error: ${f.body}'),
        onDisconnect: (f) => debugPrint('Disconnected'),
        reconnectDelay: const Duration(seconds: 2),
      ),
    );

    _client!.activate();
  }

  void _onConnect(StompFrame frame) {
    debugPrint('Connected to STOMP');

    // OPTION A (recommended): subscribe each area topic
    for (final area in _pm10ByArea.keys) {
      _client!.subscribe(
        destination: '/topic/skopje/$area',
        callback: (msg) => _handleMessage(area, msg.body),
      );
    }

    // OPTION B (if you have a single topic only):
    // _client!.subscribe(
    //   destination: '/topic/alerts/Skopje',
    //   callback: (msg) => _handleSingleTopic(msg.body),
    // );
  }

  void _handleMessage(String area, String? body) {
    if (body == null || body.isEmpty) return;
    try {
      final jsonMap = jsonDecode(body) as Map<String, dynamic>;
      final pm10 = (jsonMap['pm10'] as num?)?.toDouble();
      if (pm10 == null) return;

      setState(() {
        _pm10ByArea[area] = pm10;
      });
    } catch (e) {
      debugPrint('Parse error: $e body=$body');
    }
  }

  // If your gateway sends ALL areas in one topic:
  void _handleSingleTopic(String? body) {
    if (body == null || body.isEmpty) return;
    try {
      final jsonMap = jsonDecode(body) as Map<String, dynamic>;
      final area = (jsonMap['area'] as String?)?.toLowerCase();
      final pm10 = (jsonMap['pm10'] as num?)?.toDouble();
      if (area == null || pm10 == null) return;

      if (_pm10ByArea.containsKey(area)) {
        setState(() => _pm10ByArea[area] = pm10);
      }
    } catch (e) {
      debugPrint('Parse error: $e body=$body');
    }
  }

  Color _colorForPm10(double pm10) {
    if (pm10.isNaN) return Colors.grey.withOpacity(0.25);

    // Simple thresholds (adjust as you want)
    if (pm10 < 25) return Colors.green.withOpacity(0.35);
    if (pm10 < 50) return Colors.yellow.withOpacity(0.35);
    if (pm10 < 100) return Colors.orange.withOpacity(0.35);
    return Colors.red.withOpacity(0.40);
  }

  List<Polygon> _buildPolygons() {
    // Rough demo polygons near Skopje
    // (We’ll replace with real borders later.)
    Polygon poly(String area, List<LatLng> pts) {
      final pm10 = _pm10ByArea[area] ?? double.nan;
      return Polygon(
        points: pts,
        color: _colorForPm10(pm10),
        borderColor: Colors.black.withValues(alpha: .35),
        borderStrokeWidth: 1.5,
        label: area,
      );
    }

    return [
      poly('centar', [
        const LatLng(42.0065, 21.4150),
        const LatLng(42.0065, 21.4380),
        const LatLng(41.9910, 21.4380),
        const LatLng(41.9910, 21.4150),
      ]),
      poly('karpos', [
        const LatLng(42.0125, 21.3900),
        const LatLng(42.0125, 21.4150),
        const LatLng(41.9950, 21.4150),
        const LatLng(41.9950, 21.3900),
      ]),
      poly('gazi_baba', [
        const LatLng(42.0200, 21.4380),
        const LatLng(42.0200, 21.4750),
        const LatLng(42.0000, 21.4750),
        const LatLng(42.0000, 21.4380),
      ]),
      poly('aerodrom', [
        const LatLng(41.9950, 21.4380),
        const LatLng(41.9950, 21.4750),
        const LatLng(41.9750, 21.4750),
        const LatLng(41.9750, 21.4380),
      ]),
      poly('kisela_voda', [
        const LatLng(41.9750, 21.4200),
        const LatLng(41.9750, 21.4550),
        const LatLng(41.9550, 21.4550),
        const LatLng(41.9550, 21.4200),
      ]),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final polys = _buildPolygons();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Skopje Live PM10 Map'),
      ),
      body: Stack(
        children: [
          FlutterMap(
            options: MapOptions(
              initialCenter: _skCenter,
              initialZoom: 12.3,
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.example.weather_alerts_frontend',
              ),
              PolygonLayer(polygons: polys),
            ],
          ),

          // Simple legend + live values
          Positioned(
            left: 12,
            top: 12,
            child: _LegendCard(values: _pm10ByArea),
          ),
        ],
      ),
    );
  }
}

class _LegendCard extends StatelessWidget {
  final Map<String, double> values;
  const _LegendCard({required this.values});

  String fmt(double v) => v.isNaN ? '-' : v.toStringAsFixed(1);

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: DefaultTextStyle(
          style: const TextStyle(fontSize: 13),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Live PM10', style: TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 6),
              for (final e in values.entries)
                Text('${e.key}: ${fmt(e.value)}'),
              const SizedBox(height: 8),
              const Text('Colors: green<25, yellow<50, orange<100, red>=100'),
            ],
          ),
        ),
      ),
    );
  }
}
