# 🚀 Complete WeatherAlerts Workflow - Start to Finish

## 📋 Table of Contents
1. [Prerequisites](#prerequisites)
2. [Starting the System](#starting-the-system)
3. [Verifying Everything Works](#verifying-everything-works)
4. [Complete Data Flow Walkthrough](#complete-data-flow-walkthrough)
5. [RabbitMQ Management UI Guide](#rabbitmq-management-ui-guide)
6. [Monitoring in Terminal](#monitoring-in-terminal)
7. [Troubleshooting](#troubleshooting)

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

**🎉 Your complete workflow is now documented! Follow this guide step-by-step to understand and run your WeatherAlerts system.**

