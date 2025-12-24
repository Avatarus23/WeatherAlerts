# 🚀 Complete WeatherAlerts Workflow - Start to Finish

## 📋 Table of Contents
1. [System Architecture Diagram](#system-architecture-diagram)
2. [Complete Workflow Diagram](#complete-workflow-diagram)
3. [Prerequisites](#prerequisites)
4. [Starting the System](#starting-the-system)
5. [Verifying Everything Works](#verifying-everything-works)
6. [Complete Data Flow Walkthrough](#complete-data-flow-walkthrough)
7. [RabbitMQ Management UI Guide](#rabbitmq-management-ui-guide)
8. [Monitoring in Terminal](#monitoring-in-terminal)
9. [Troubleshooting](#troubleshooting)

---

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         WEATHER ALERTS SYSTEM ARCHITECTURE                    │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│  PulseEco API   │  External Data Source
│  (Weather Data) │
└────────┬────────┘
         │
         │ HTTP GET (every 60s)
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PRODUCER SERVICE (Port 8082)                          │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  CityProducerScheduler                                             │   │
│  │  - Fetches data every 60 seconds                                    │   │
│  │  - Parses measurements                                              │   │
│  │  - Resolves area (centar, gazi_baba, etc.)                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Creates CityMeasurement                       │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  MeasurementProducer                                                │   │
│  │  - Publishes to RabbitMQ                                            │   │
│  │  - Routing Key: reading.{area}.{metric}                            │   │
│  │  - Example: reading.centar.pm10                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     │ RabbitMQ Publish
                                     │ Exchange: readings.topic
                                     │ Routing Key: reading.centar.pm10
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           RABBITMQ MESSAGE BROKER                            │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Exchange: readings.topic (Topic Exchange)                          │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  Binding: reading.# → Queue: agg.readings                    │   │   │
│  │  │  (Receives ALL readings)                                     │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Routes messages                               │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Queue: agg.readings                                               │   │
│  │  - Stores readings until consumed                                  │   │
│  │  - Dead Letter Queue: dlq.agg.readings                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     │ Consumes messages
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      AGGREGATOR SERVICE (Port 8080)                         │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  ReadingListener                                                    │   │
│  │  - Consumes from agg.readings queue                                │   │
│  │  - Validates messages                                               │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Passes to AggregatorService                   │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AggregatorService                                                  │   │
│  │  - Maintains sliding window (last 10 readings)                     │   │
│  │  - Calculates average per area+metric                              │   │
│  │  - Determines alert level:                                         │   │
│  │    • PM10 > 50 = RED                                               │   │
│  │    • PM10 ≤ 50 = GREEN                                             │   │
│  │  - Publishes alert ONLY when level changes                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Publishes AlertMessage                        │
│                              │ Routing Key: alert.{area}.{level}            │
│                              │ Example: alert.gazi_baba.RED                  │
│                              ▼                                               │
└────────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     │ RabbitMQ Publish
                                     │ Exchange: alerts.topic
                                     │ Routing Key: alert.gazi_baba.RED
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           RABBITMQ MESSAGE BROKER                            │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Exchange: alerts.topic (Topic Exchange)                           │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  Binding: alert.*.* → Queue: gw.alerts                       │   │   │
│  │  │  (Receives ALL alerts)                                       │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Routes alerts                                 │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Queue: gw.alerts                                                  │   │
│  │  - Stores alerts until consumed                                    │   │
│  │  - Dead Letter Queue: dlq.gw.alerts                                │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     │ Consumes alerts
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        GATEWAY SERVICE (Port 8081)                          │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  AlertForwarder                                                     │   │
│  │  - Consumes from gw.alerts queue                                   │   │
│  │  - Forwards to WebSocket topics:                                   │   │
│  │    • /topic/alerts/{area} (area-specific)                          │   │
│  │    • /topic/alerts/all (broadcast)                                 │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ WebSocket (STOMP)                             │
│                              │ Endpoint: ws://localhost:8081/ws              │
│                              ▼                                               │
└────────────────────────────────────┬──────────────────────────────────────────┘
                                     │
                                     │ Real-time push
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUTTER FRONTEND (Web/Mobile)                            │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  WebSocket Client                                                   │   │
│  │  - Connects to: ws://localhost:8081/ws                             │   │
│  │  - Subscribes to: /topic/alerts/{area}                             │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                              │                                               │
│                              │ Receives alerts                               │
│                              ▼                                               │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  Map UI                                                             │   │
│  │  - Updates colors based on alert level                             │   │
│  │  - Green = GREEN alert (safe)                                      │   │
│  │  - Red = RED alert (warning)                                      │   │
│  │  - Shows PM10 values in legend                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Complete Workflow Diagram

### High-Level Flow

```
┌──────────────┐
│  PulseEco    │
│     API      │
└──────┬───────┘
       │
       │ [1] Fetch Data (every 60s)
       ▼
┌──────────────────┐      ┌──────────────┐      ┌──────────────────┐
│   PRODUCER       │─────►│   RABBITMQ    │─────►│   AGGREGATOR     │
│   SERVICE        │      │   BROKER      │      │   SERVICE        │
│                  │      │               │      │                  │
│ • Fetches data   │      │ Exchange:     │      │ • Consumes       │
│ • Every 60s      │      │ readings.     │      │   readings       │
│ • Publishes:     │      │ topic         │      │ • Aggregates     │
│   reading.*.*    │      │               │      │   (sliding       │
└──────────────────┘      │ Queue:        │      │    window)       │
                          │ agg.readings  │      │ • Generates      │
                          │               │      │   alerts         │
                          └───────┬───────┘      │ • Publishes:     │
                                  │               │   alert.*.*      │
                                  │               └────────┬─────────┘
                                  │                        │
                                  │               ┌────────▼─────────┐
                                  │               │  RABBITMQ         │
                                  │               │  Exchange:        │
                                  │               │  alerts.topic     │
                                  │               └────────┬─────────┘
                                  │                        │
                    ┌─────────────▼──────────┐           │
                    │   GATEWAY SERVICE      │◄──────────┘
                    │                        │
                    │ • Consumes alerts      │
                    │ • Forwards to          │
                    │   WebSocket            │
                    │   /topic/alerts/*     │
                    └───────────┬────────────┘
                                │
                                │ WebSocket (STOMP)
                                │
                                ▼
                    ┌───────────────────────┐
                    │   FLUTTER FRONTEND    │
                    │                       │
                    │ • Subscribes to       │
                    │   WebSocket topics    │
                    │ • Updates map UI      │
                    │ • Shows live values   │
                    └───────────────────────┘
```

### Detailed Message Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         DETAILED MESSAGE FLOW                           │
└─────────────────────────────────────────────────────────────────────────┘

STEP 1: DATA COLLECTION
────────────────────────
PulseEco API
    │
    │ HTTP GET /current/{city}
    ▼
Producer Service
    │
    │ Creates: CityMeasurement {
    │   city: "SKOPJE",
    │   area: "centar",
    │   metric: "pm10",
    │   value: 45.2,
    │   timestamp: "2024-12-24T01:00:00Z"
    │ }
    │
    │ Routing Key: "reading.centar.pm10"
    ▼
RabbitMQ Exchange: readings.topic
    │
    │ Pattern Matching: reading.# matches reading.centar.pm10
    ▼
RabbitMQ Queue: agg.readings
    │
    │ [Message stored in queue]
    │ JSON: {"city":"SKOPJE","area":"centar","metric":"pm10","value":45.2,...}
    │
    └─────────────────────────────────────────────────────────────────────┘

STEP 2: AGGREGATION
───────────────────
Aggregator Service (ReadingListener)
    │
    │ Consumes message from agg.readings
    │
    │ Parses: area="centar", metric="pm10", value=45.2
    ▼
AggregatorService.process()
    │
    │ Key: "centar|pm10"
    │
    │ Sliding Window (last 10 readings):
    │   [45.2, 48.5, 47.1, 46.8, 49.2, 45.9, 47.5, 46.2, 48.1, 47.8]
    │
    │ Average: 47.23
    │
    │ Threshold Check:
    │   PM10 > 50? NO → Level = GREEN
    │
    │ Last Level: GREEN
    │ Current Level: GREEN
    │
    │ Status: No change → No alert published
    │
    └─────────────────────────────────────────────────────────────────────┘

STEP 3: ALERT GENERATION (When threshold exceeded)
───────────────────────────────────────────────────
AggregatorService.process()
    │
    │ Key: "gazi_baba|pm10"
    │
    │ Sliding Window:
    │   [52.8, 54.2, 53.1, 55.5, 52.3, 54.8, 53.9, 55.1, 54.5, 53.7]
    │
    │ Average: 54.03
    │
    │ Threshold Check:
    │   PM10 > 50? YES → Level = RED
    │
    │ Last Level: GREEN
    │ Current Level: RED
    │
    │ Status: Level changed → Publish alert!
    │
    │ Creates: AlertMessage {
    │   area: "gazi_baba",
    │   metric: "pm10",
    │   level: "RED",
    │   value: 54.03,
    │   threshold: 50.0,
    │   timestamp: "2024-12-24T01:00:00Z",
    │   reason: "Average pm10 over last 10 readings = 54.03"
    │ }
    │
    │ Routing Key: "alert.gazi_baba.RED"
    ▼
RabbitMQ Exchange: alerts.topic
    │
    │ Pattern Matching: alert.*.* matches alert.gazi_baba.RED
    ▼
RabbitMQ Queue: gw.alerts
    │
    │ [Alert stored in queue]
    │ JSON: {"area":"gazi_baba","level":"RED","metric":"pm10","value":54.03,...}
    │
    └─────────────────────────────────────────────────────────────────────┘

STEP 4: WEBSOCKET FORWARDING
─────────────────────────────
Gateway Service (AlertForwarder)
    │
    │ Consumes alert from gw.alerts
    │
    │ Receives: AlertMessage {
    │   area: "gazi_baba",
    │   level: "RED",
    │   ...
    │ }
    │
    │ Normalizes: areaKey = "gazi_baba"
    │
    │ WebSocket Destinations:
    │   • /topic/alerts/gazi_baba (area-specific)
    │   • /topic/alerts/all (broadcast)
    ▼
WebSocket Server (STOMP)
    │
    │ Sends to subscribed clients:
    │   • Clients subscribed to /topic/alerts/gazi_baba
    │   • Clients subscribed to /topic/alerts/all
    │
    └─────────────────────────────────────────────────────────────────────┘

STEP 5: FRONTEND DISPLAY
────────────────────────
Flutter App
    │
    │ WebSocket Client connected to: ws://localhost:8081/ws
    │
    │ Subscribed to:
    │   • /topic/alerts/centar
    │   • /topic/alerts/gazi_baba
    │   • /topic/alerts/karposh
    │   • /topic/alerts/all
    │
    │ Receives alert:
    │   {
    │     "area": "gazi_baba",
    │     "level": "RED",
    │     "metric": "pm10",
    │     "value": 54.03
    │   }
    │
    │ Updates UI:
    │   • Map: gazi_baba area → Red color
    │   • Legend: gazi_baba: 54.03
    │
    └─────────────────────────────────────────────────────────────────────┘
```

### RabbitMQ Routing Patterns

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    RABBITMQ ROUTING PATTERNS                             │
└─────────────────────────────────────────────────────────────────────────┘

EXCHANGE: readings.topic (Topic Exchange)
──────────────────────────────────────────
Producer publishes with routing keys:
  • reading.centar.pm10
  • reading.gazi_baba.pm10
  • reading.karposh.temperature
  • reading.aerodrom.pm25

Queue Binding: agg.readings
  Pattern: reading.#
  
  reading.# matches:
    ✅ reading.centar.pm10
    ✅ reading.gazi_baba.pm10
    ✅ reading.karposh.temperature
    ✅ reading.aerodrom.pm25
    ✅ reading.anything.anything
    
  Result: Queue receives ALL readings


EXCHANGE: alerts.topic (Topic Exchange)
─────────────────────────────────────────
Aggregator publishes with routing keys:
  • alert.centar.RED
  • alert.gazi_baba.GREEN
  • alert.karposh.YELLOW

Queue Binding: gw.alerts
  Pattern: alert.*.*
  
  alert.*.* matches:
    ✅ alert.centar.RED
    ✅ alert.gazi_baba.GREEN
    ✅ alert.karposh.YELLOW
    ✅ alert.any_area.any_level
    
  Result: Queue receives ALL alerts

Pattern Explanation:
  • * = matches exactly one word
  • # = matches zero or more words
```

### Timeline View

```
TIME: 00:00:00
────────────────
Producer: Fetches data from PulseEco API
Producer: Publishes 287 measurements to RabbitMQ
          └─> readings.topic exchange
              └─> Routes to agg.readings queue

TIME: 00:00:01
────────────────
Aggregator: Consumes first reading (centar, pm10, 45.2)
Aggregator: Window size: 1, Average: 45.2, Level: GREEN
Aggregator: No previous level → Publish alert (GREEN)

TIME: 00:00:02
────────────────
Gateway: Consumes alert from gw.alerts
Gateway: Forwards to /topic/alerts/centar and /topic/alerts/all
Flutter: Receives alert, updates map (centar = green)

TIME: 00:00:03 - 00:00:10
──────────────────────────
Aggregator: Receives 8 more readings
Aggregator: Window fills up (size: 10)
Aggregator: Average: 47.3, Level: GREEN
Aggregator: Last level was GREEN → No alert (deduplication)

TIME: 00:00:11
────────────────
Aggregator: Receives reading (gazi_baba, pm10, 52.8)
Aggregator: Window: [52.8, 54.2, 53.1, ...], Average: 54.03
Aggregator: Level: RED (54.03 > 50)
Aggregator: Last level was GREEN → Level changed → Publish alert!

TIME: 00:00:12
────────────────
Gateway: Consumes RED alert
Gateway: Forwards to /topic/alerts/gazi_baba and /topic/alerts/all
Flutter: Receives alert, updates map (gazi_baba = red)

TIME: 00:01:00 (Next cycle)
────────────────────────────
Producer: Fetches new data (60 seconds passed)
Producer: Publishes new measurements
[Cycle repeats...]
```

---

## Prerequisites

Before starting, ensure you have:
- ✅ Java 17 installed
- ✅ Docker installed and running
- ✅ Maven (or use included `mvnw`)
- ✅ Flutter installed (for frontend)

---

## Starting the System

### Step 1: Start RabbitMQ

**Open Terminal 1:**

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts
docker-compose up -d
```

**Wait 10-15 seconds** for RabbitMQ to fully start.

**Verify RabbitMQ is running:**
```bash
docker ps | grep rabbitmq
# Should show: rabbitmq container running
```

**Expected output:**
```
CONTAINER ID   IMAGE                    STATUS         PORTS
abc123def456   rabbitmq:3-management    Up 2 minutes   0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
```

---

### Step 2: Start Gateway Service

**Open Terminal 2:**

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/gateway-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started GatewayServiceApplication in X.XXX seconds
```

**What you'll see:**
- Spring Boot banner
- Connection to RabbitMQ
- WebSocket configuration
- Service started on port 8081

---

### Step 3: Start Aggregator Service

**Open Terminal 3:**

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/aggregator-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started AggregatorServiceApplication in X.XXX seconds
```

**What you'll see:**
- Spring Boot banner
- Connection to RabbitMQ
- Queue bindings created
- Service started on port 8080

---

### Step 4: Start Producer Service

**Open Terminal 4:**

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/producer-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started ProducerServiceApplication in X.XXX seconds
```

**What you'll see:**
- Spring Boot banner
- Connection to RabbitMQ
- First data fetch (happens immediately)
- Service started on port 8082

**You should immediately see logs like:**
```
INFO - Fetched 287 measurements for city SKOPJE
INFO - ✅ Published measurement: routingKey=reading.centar.pm10, area=centar, city=SKOPJE, metric=pm10, value=45.2
```

---

### Step 5: Start Flutter Frontend

**Open Terminal 5:**

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/weather_alerts_frontend

# Install dependencies (first time only)
flutter pub get

# Run on web browser
flutter run -d chrome
```

**What you'll see:**
- Flutter build process
- Browser opens automatically
- Map of Skopje with colored areas
- Console shows: "Connected to STOMP"

---

## Verifying Everything Works

### Quick Health Check

**In a new terminal, run:**

```bash
# Check RabbitMQ
curl -u weather:weather123 http://localhost:15672/api/overview | head -20

# Check if services are responding (even without actuator)
curl http://localhost:8081/  # Gateway - might return error, but service is up
curl http://localhost:8080/  # Aggregator - might return error, but service is up
curl http://localhost:8082/  # Producer - might return error, but service is up
```

**Note:** Actuator is optional. If you don't need monitoring endpoints, you can skip it. The services work fine without it.

---

## Complete Data Flow Walkthrough

### Phase 1: Data Collection (Producer Service)

**What happens:**
1. Producer service starts
2. Every 60 seconds, it fetches data from PulseEco API
3. For each measurement, it creates a `CityMeasurement` object
4. Publishes to RabbitMQ exchange `readings.topic`

**Watch in Terminal 4 (Producer):**

```bash
# You'll see logs like this every 60 seconds:
INFO - Fetched 287 measurements for city SKOPJE
INFO - ✅ Published measurement: routingKey=reading.centar.pm10, area=centar, city=SKOPJE, metric=pm10, value=45.2, sensor=sensor-123
INFO - ✅ Published measurement: routingKey=reading.gazi_baba.pm10, area=gazi_baba, city=SKOPJE, metric=pm10, value=52.8, sensor=sensor-456
INFO - ✅ Published measurement: routingKey=reading.karposh.pm10, area=karposh, city=SKOPJE, metric=pm10, value=38.5, sensor=sensor-789
```

**What's happening:**
- Producer creates routing keys: `reading.{area}.{metric}`
- Example: `reading.centar.pm10`, `reading.gazi_baba.temperature`
- Messages are JSON-serialized automatically
- Published to exchange: `readings.topic`

---

### Phase 2: Message Routing (RabbitMQ)

**What happens:**
1. Exchange `readings.topic` receives messages
2. Routes messages to queues based on bindings
3. Queue `agg.readings` receives all messages (binding: `reading.#`)

**Watch in RabbitMQ UI:**
- Go to http://localhost:15672
- Login: `weather` / `weather123`
- Click "Queues" tab
- See `agg.readings` queue with messages

**Message flow:**
```
Producer → Exchange (readings.topic) → Queue (agg.readings) → Aggregator
```

---

### Phase 3: Aggregation & Alert Generation (Aggregator Service)

**What happens:**
1. Aggregator consumes messages from `agg.readings` queue
2. Maintains sliding window (last 10 readings) per area+metric
3. Calculates average
4. Determines alert level (PM10 > 50 = RED, else GREEN)
5. Publishes alert ONLY when level changes

**Watch in Terminal 3 (Aggregator):**

```bash
# You'll see logs like:
INFO - 📥 Received reading: routingKey=reading.centar.pm10, area=centar, metric=pm10, value=45.2, city=SKOPJE
INFO - 📊 No level change: area=centar, metric=pm10, level=GREEN, avg=47.3, windowSize=10

# When threshold exceeded:
INFO - 📥 Received reading: routingKey=reading.gazi_baba.pm10, area=gazi_baba, metric=pm10, value=52.8, city=SKOPJE
INFO - 🚨 Alert published: routingKey=alert.gazi_baba.RED, area=gazi_baba, metric=pm10, level=RED, avg=52.34, windowSize=10
```

**What's happening:**
- Aggregator receives reading
- Adds to sliding window
- Calculates average of last 10 readings
- If average > 50 (for PM10), level = RED
- Publishes alert with routing key: `alert.{area}.{level}`
- Example: `alert.gazi_baba.RED`, `alert.centar.GREEN`

---

### Phase 4: Alert Distribution (RabbitMQ → Gateway)

**What happens:**
1. Exchange `alerts.topic` receives alerts
2. Routes to queue `gw.alerts` (binding: `alert.*.*`)
3. Gateway consumes from `gw.alerts` queue

**Watch in RabbitMQ UI:**
- Click "Queues" tab
- See `gw.alerts` queue
- Messages appear when alerts are published
- Messages disappear as gateway consumes them

---

### Phase 5: WebSocket Forwarding (Gateway Service)

**What happens:**
1. Gateway consumes alert from `gw.alerts` queue
2. Forwards to WebSocket topics:
   - `/topic/alerts/{area}` (area-specific)
   - `/topic/alerts/all` (broadcast)

**Watch in Terminal 2 (Gateway):**

```bash
# You'll see logs like:
INFO - ✅ Alert forwarded: area=gazi_baba, level=RED, metric=pm10, value=52.34, destinations=[/topic/alerts/gazi_baba, /topic/alerts/all]
```

**What's happening:**
- Gateway receives alert from RabbitMQ
- Converts to WebSocket message
- Sends to area-specific topic (e.g., `/topic/alerts/gazi_baba`)
- Sends to broadcast topic (`/topic/alerts/all`)

---

### Phase 6: Frontend Display (Flutter App)

**What happens:**
1. Flutter connects to WebSocket: `ws://localhost:8081/ws`
2. Subscribes to topics: `/topic/alerts/{area}`
3. Receives alerts
4. Updates map with colors based on alert level

**Watch in Browser:**
- Map shows colored areas
- Green = safe (GREEN alert)
- Red = warning (RED alert)
- Legend shows PM10 values

**Watch in Terminal 5 (Flutter console):**
```
Connected to STOMP
```

---

## RabbitMQ Management UI Guide

### Accessing RabbitMQ UI

1. **Open browser:** http://localhost:15672
2. **Login:**
   - Username: `weather`
   - Password: `weather123`

---

### Overview Tab

**What to check:**
- **Connections:** Should show 3 (one per service)
- **Channels:** Should show active channels
- **Queues:** Should show 4 queues
- **Exchanges:** Should show 3 exchanges

---

### Exchanges Tab

**Click "Exchanges" tab:**

You should see:

1. **readings.topic** (Topic Exchange)
   - **Type:** topic
   - **Durable:** Yes
   - **Bindings:** 1 (to `agg.readings` queue)
   - **Message rate:** Messages published here

2. **alerts.topic** (Topic Exchange)
   - **Type:** topic
   - **Durable:** Yes
   - **Bindings:** 1 (to `gw.alerts` queue)
   - **Message rate:** Alerts published here

3. **dlx** (Direct Exchange - Dead Letter Exchange)
   - **Type:** direct
   - **Durable:** Yes
   - **Bindings:** 2 (to DLQ queues)

**To see bindings:**
- Click on exchange name
- See "Bindings" section
- Shows which queues are bound and with what pattern

---

### Queues Tab

**Click "Queues" tab:**

You should see 4 queues:

1. **agg.readings**
   - **Messages:** Should have messages coming in
   - **Consumers:** 1 (aggregator service)
   - **Message rate:** Messages/sec
   - **Binding:** `readings.topic` with pattern `reading.#`

2. **gw.alerts**
   - **Messages:** Should have alerts (consumed quickly)
   - **Consumers:** 1 (gateway service)
   - **Message rate:** Alerts/sec
   - **Binding:** `alerts.topic` with pattern `alert.*.*`

3. **dlq.agg.readings** (Dead Letter Queue)
   - **Messages:** Should be 0 (unless errors)
   - **Purpose:** Stores failed messages from aggregator

4. **dlq.gw.alerts** (Dead Letter Queue)
   - **Messages:** Should be 0 (unless errors)
   - **Purpose:** Stores failed messages from gateway

**To inspect messages:**
1. Click on queue name
2. Scroll down to "Get messages"
3. Click "Get message(s)"
4. See message content (JSON)

**Example message in `agg.readings`:**
```json
{
  "city": "SKOPJE",
  "area": "centar",
  "sensorId": "sensor-123",
  "position": "41.9981,21.4254",
  "timestamp": "2024-12-24T01:00:00Z",
  "metric": "pm10",
  "value": 45.2
}
```

**Example message in `gw.alerts`:**
```json
{
  "area": "gazi_baba",
  "metric": "pm10",
  "level": "RED",
  "value": 52.34,
  "threshold": 50.0,
  "timestamp": "2024-12-24T01:00:00Z",
  "reason": "Average pm10 over last 10 readings = 52.34 (threshold: 50.0)"
}
```

---

### Connections Tab

**Click "Connections" tab:**

You should see 3 connections:
1. **Gateway Service** - Connected to port 8081
2. **Aggregator Service** - Connected to port 8080
3. **Producer Service** - Connected to port 8082

**To see details:**
- Click on connection name
- See channels, message rates, etc.

---

### Monitoring Message Flow

**Real-time monitoring:**

1. **Go to Queues tab**
2. **Watch `agg.readings` queue:**
   - Messages should appear every few seconds
   - Consumer should process them quickly
   - Message count should stay low (good throughput)

3. **Watch `gw.alerts` queue:**
   - Alerts appear when threshold exceeded
   - Consumer processes immediately
   - Queue should be mostly empty

4. **Check message rates:**
   - Click on queue
   - See "Message rates" graph
   - Shows messages/sec over time

---

## Monitoring in Terminal

### Watch Producer Logs

**In Terminal 4 (Producer):**

```bash
# Watch for published messages
# Every 60 seconds you'll see:
INFO - Fetched X measurements for city SKOPJE
INFO - ✅ Published measurement: routingKey=reading.centar.pm10, ...
```

**What to look for:**
- ✅ Successful publishes
- ⚠️ HTTP 401 errors (some cities might not work)
- ❌ Connection errors (RabbitMQ down)

---

### Watch Aggregator Logs

**In Terminal 3 (Aggregator):**

```bash
# Watch for received readings and alerts
INFO - 📥 Received reading: routingKey=reading.centar.pm10, ...
INFO - 🚨 Alert published: routingKey=alert.centar.RED, ...
```

**What to look for:**
- ✅ Readings received
- ✅ Alerts published (when threshold exceeded)
- 📊 No level change (normal - prevents duplicate alerts)
- ❌ Processing errors

---

### Watch Gateway Logs

**In Terminal 2 (Gateway):**

```bash
# Watch for forwarded alerts
INFO - ✅ Alert forwarded: area=centar, level=RED, ...
```

**What to look for:**
- ✅ Alerts forwarded to WebSocket
- ❌ WebSocket connection errors
- ❌ Forwarding failures

---

### Watch Flutter Console

**In Terminal 5 (Flutter):**

```bash
# Watch for WebSocket connection
Connected to STOMP

# If errors:
WS error: ...
STOMP error: ...
```

**What to look for:**
- ✅ "Connected to STOMP"
- ✅ Receiving alerts
- ❌ Connection errors

---

## Complete Workflow Example

### Scenario: PM10 Alert Generation

**Step 1: Producer fetches data**
```
Terminal 4 (Producer):
INFO - Fetched 287 measurements for city SKOPJE
INFO - ✅ Published measurement: routingKey=reading.gazi_baba.pm10, value=52.8
```

**Step 2: RabbitMQ routes message**
```
RabbitMQ UI → Queues → agg.readings
Message appears in queue
```

**Step 3: Aggregator processes**
```
Terminal 3 (Aggregator):
INFO - 📥 Received reading: routingKey=reading.gazi_baba.pm10, value=52.8
INFO - 🚨 Alert published: routingKey=alert.gazi_baba.RED, avg=52.34
```

**Step 4: RabbitMQ routes alert**
```
RabbitMQ UI → Queues → gw.alerts
Alert appears in queue
```

**Step 5: Gateway forwards**
```
Terminal 2 (Gateway):
INFO - ✅ Alert forwarded: area=gazi_baba, level=RED, destinations=[/topic/alerts/gazi_baba, /topic/alerts/all]
```

**Step 6: Flutter receives**
```
Browser: Map updates, gazi_baba area turns red
Flutter console: Receives alert message
```

---

## Troubleshooting

### Problem: No messages in queues

**Check:**
1. Is RabbitMQ running? `docker ps | grep rabbitmq`
2. Are services connected? Check RabbitMQ UI → Connections
3. Is producer publishing? Check Terminal 4 logs
4. Are bindings correct? Check RabbitMQ UI → Exchanges → Bindings

**Solution:**
```bash
# Restart RabbitMQ
docker-compose restart rabbitmq

# Restart services
# Ctrl+C in each terminal, then restart
```

---

### Problem: Messages stuck in queue

**Check:**
1. Is consumer running? Check Terminal 2/3
2. Are there errors? Check service logs
3. Is queue bound correctly? Check RabbitMQ UI

**Solution:**
```bash
# Check consumer status
# Look for "Waiting for workers" in logs

# Restart consumer service
# Ctrl+C and restart
```

---

### Problem: No alerts being generated

**Check:**
1. Are readings being received? Check Terminal 3
2. Is average above threshold? Check aggregator logs
3. Is alert deduplication working? (Only publishes on level change)

**Solution:**
- Check aggregator logs for "No level change" messages
- This is normal - alerts only publish when level changes
- Wait for threshold to be exceeded

---

### Problem: Flutter not receiving alerts

**Check:**
1. Is WebSocket connected? Check Flutter console
2. Is gateway forwarding? Check Terminal 2
3. Is Flutter subscribed to correct topics?

**Solution:**
```bash
# Check WebSocket endpoint
curl http://localhost:8081/ws
# Should return WebSocket upgrade response

# Restart Flutter
# Ctrl+C and restart
```

---

## Quick Reference Commands

### Start Everything
```bash
# Terminal 1: RabbitMQ
docker-compose up -d

# Terminal 2: Gateway
cd gateway-service && ./mvnw spring-boot:run

# Terminal 3: Aggregator
cd aggregator-service && ./mvnw spring-boot:run

# Terminal 4: Producer
cd producer-service && ./mvnw spring-boot:run

# Terminal 5: Flutter
cd weather_alerts_frontend && flutter run -d chrome
```

### Stop Everything
```bash
# Stop services (Ctrl+C in each terminal)

# Stop RabbitMQ
docker-compose down
```

### Check Status
```bash
# RabbitMQ
docker ps | grep rabbitmq

# Services (check if ports are in use)
lsof -i :8081  # Gateway
lsof -i :8080  # Aggregator
lsof -i :8082  # Producer
```

---

## Summary

**Complete Flow:**
```
Producer (fetches data) 
  → RabbitMQ Exchange (readings.topic)
    → Queue (agg.readings)
      → Aggregator (processes, generates alerts)
        → RabbitMQ Exchange (alerts.topic)
          → Queue (gw.alerts)
            → Gateway (forwards to WebSocket)
              → Flutter (displays on map)
```

**Key Points:**
- Producer fetches every 60 seconds
- Aggregator maintains sliding window (10 readings)
- Alerts only publish when level changes
- Gateway forwards to WebSocket topics
- Flutter displays live updates on map

**Monitoring:**
- Watch terminal logs for each service
- Use RabbitMQ UI to see message flow
- Check queues for message rates
- Inspect messages for debugging

---

## Do You Need Actuator?

**Short answer: No, it's optional.**

**Actuator provides:**
- Health check endpoints (`/actuator/health`)
- Metrics (`/actuator/metrics`)
- RabbitMQ info (`/actuator/rabbit`)

**You can monitor without it:**
- ✅ Watch terminal logs
- ✅ Use RabbitMQ Management UI
- ✅ Check service responses
- ✅ Monitor queues directly

**Use actuator if:**
- You want programmatic health checks
- You need metrics for monitoring tools
- You want to change log levels at runtime

**For this project:** Terminal logs + RabbitMQ UI are sufficient for monitoring!

---

**🎉 Your complete workflow is now documented with visual diagrams! Follow this guide step-by-step to understand and run your WeatherAlerts system.**

