# 🚀 Complete Startup & Usage Guide

## 📋 Prerequisites

Before starting, ensure you have:
- ✅ **Java 17** installed (`java -version`)
- ✅ **Maven** installed (or use included `mvnw`)
- ✅ **Docker** installed and running (`docker ps`)
- ✅ **Flutter** installed (for frontend) (`flutter --version`)
- ✅ **Ports available**: 5672, 15672, 8080, 8081, 8082

---

## 🎯 Quick Start (All-in-One)

### Option 1: Using the Start Script

```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts
chmod +x start-all.sh
./start-all.sh
```

Then in a separate terminal:
```bash
cd weather_alerts_frontend
flutter pub get
flutter run -d chrome
```

### Option 2: Manual Step-by-Step (Recommended for First Time)

---

## 📝 Step-by-Step Startup

### Step 1: Start RabbitMQ

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

**Access RabbitMQ Management UI:**
- URL: http://localhost:15672
- Username: `weather`
- Password: `weather123`

---

### Step 2: Start Gateway Service

**Open Terminal 1:**
```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/gateway-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started GatewayServiceApplication in X.XXX seconds
```

**Service runs on:** http://localhost:8081

---

### Step 3: Start Aggregator Service

**Open Terminal 2:**
```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/aggregator-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started AggregatorServiceApplication in X.XXX seconds
```

**Service runs on:** http://localhost:8080

---

### Step 4: Start Producer Service

**Open Terminal 3:**
```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/producer-service
./mvnw spring-boot:run
```

**Wait for this message:**
```
Started ProducerServiceApplication in X.XXX seconds
```

**Service runs on:** http://localhost:8082

**You should see logs like:**
```
Fetched X measurements for city SKOPJE
✅ Published measurement: routingKey=reading.centar.pm10, ...
```

---

### Step 5: Start Flutter Frontend

**Open Terminal 4:**
```bash
cd /Users/bobi/Home/Finki/Semestar\ 7/distribuirani-sistemi/WeatherAlerts/weather_alerts_frontend

# Install dependencies (first time only)
flutter pub get

# Run on web browser
flutter run -d chrome

# OR run on macOS desktop
flutter run -d macos

# OR run on connected device/emulator
flutter run
```

---

## ✅ Verification Checklist

### 1. Check All Services Are Running

**RabbitMQ:**
```bash
curl http://localhost:15672/api/overview
# Should return JSON with RabbitMQ info
```

**Gateway Service:**
```bash
curl http://localhost:8081/actuator/health
# Should return: {"status":"UP"}
```

**Aggregator Service:**
```bash
curl http://localhost:8080/actuator/health
# Should return: {"status":"UP"}
```

**Producer Service:**
```bash
curl http://localhost:8082/actuator/health
# Should return: {"status":"UP"}
```

### 2. Check RabbitMQ Queues

**Via Management UI:**
1. Go to http://localhost:15672
2. Login: `weather` / `weather123`
3. Click "Queues" tab
4. You should see:
   - `agg.readings` (aggregator queue)
   - `gw.alerts` (gateway queue)
   - `dlq.agg.readings` (dead letter queue)
   - `dlq.gw.alerts` (dead letter queue)

**Via Command Line:**
```bash
# List queues
docker exec rabbitmq rabbitmqctl list_queues name messages
```

### 3. Check Message Flow

**Watch Producer Logs:**
You should see every 60 seconds:
```
✅ Published measurement: routingKey=reading.centar.pm10, area=centar, city=SKOPJE, metric=pm10, value=XX.XX
```

**Watch Aggregator Logs:**
You should see:
```
📥 Received reading: routingKey=reading.centar.pm10, area=centar, metric=pm10, value=XX.XX
🚨 Alert published: routingKey=alert.centar.RED, area=centar, metric=pm10, level=RED, avg=XX.XX
```

**Watch Gateway Logs:**
You should see:
```
✅ Alert forwarded: area=centar, level=RED, metric=pm10, value=XX.XX, destinations=[/topic/alerts/centar, /topic/alerts/all]
```

**Watch Flutter Console:**
You should see:
```
Connected to STOMP
```

---

## 📊 Monitoring & Health Checks

### Actuator Endpoints

All services expose monitoring endpoints:

#### **Health Check**
```bash
# Gateway
curl http://localhost:8081/actuator/health | jq

# Aggregator
curl http://localhost:8080/actuator/health | jq

# Producer
curl http://localhost:8082/actuator/health | jq
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "rabbit": {
      "status": "UP",
      "details": {
        "version": "3.x.x"
      }
    }
  }
}
```

#### **Metrics**
```bash
# View all metrics
curl http://localhost:8081/actuator/metrics | jq

# View specific metric
curl http://localhost:8081/actuator/metrics/jvm.memory.used | jq
```

#### **RabbitMQ Info**
```bash
curl http://localhost:8081/actuator/rabbit | jq
```

#### **Loggers (Change log levels at runtime)**
```bash
# View loggers
curl http://localhost:8081/actuator/loggers | jq

# Change log level
curl -X POST http://localhost:8081/actuator/loggers/mk.ukim.finki.gatewayservice \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## 🔍 How the System Works

### Complete Data Flow

```
1. PRODUCER SERVICE (Port 8082)
   ↓
   Fetches data from PulseEco API every 60 seconds
   ↓
   Publishes to RabbitMQ Exchange: "readings.topic"
   Routing Key: "reading.{area}.{metric}"
   Example: "reading.gazi_baba.pm10"
   ↓

2. RABBITMQ
   ↓
   Exchange: "readings.topic" (Topic Exchange)
   ↓
   Routes to Queue: "agg.readings"
   Binding Pattern: "reading.#" (receives all readings)
   ↓

3. AGGREGATOR SERVICE (Port 8080)
   ↓
   Consumes from "agg.readings" queue
   ↓
   Maintains sliding window (last 10 readings)
   ↓
   Calculates average
   ↓
   Determines alert level (PM10 > 50 = RED, else GREEN)
   ↓
   Publishes alert ONLY when level changes
   ↓
   Publishes to RabbitMQ Exchange: "alerts.topic"
   Routing Key: "alert.{area}.{level}"
   Example: "alert.gazi_baba.RED"
   ↓

4. RABBITMQ
   ↓
   Exchange: "alerts.topic" (Topic Exchange)
   ↓
   Routes to Queue: "gw.alerts"
   Binding Pattern: "alert.*.*" (receives all alerts)
   ↓

5. GATEWAY SERVICE (Port 8081)
   ↓
   Consumes from "gw.alerts" queue
   ↓
   Forwards to WebSocket topics:
   - /topic/alerts/{area} (area-specific)
   - /topic/alerts/all (broadcast)
   ↓

6. FLUTTER FRONTEND
   ↓
   Connects to WebSocket: ws://localhost:8081/ws
   ↓
   Subscribes to topics: /topic/alerts/{area}
   ↓
   Updates map UI with live data
```

---

## 🧪 Testing the System

### Test 1: Verify Message Publishing

**Watch Producer logs:**
```bash
# In Terminal 3 (Producer)
# You should see every 60 seconds:
✅ Published measurement: routingKey=reading.centar.pm10, ...
```

### Test 2: Verify Message Consumption

**Watch Aggregator logs:**
```bash
# In Terminal 2 (Aggregator)
# You should see:
📥 Received reading: routingKey=reading.centar.pm10, ...
```

### Test 3: Verify Alert Generation

**Watch Aggregator logs:**
```bash
# When average exceeds threshold:
🚨 Alert published: routingKey=alert.centar.RED, level=RED, avg=XX.XX
```

### Test 4: Verify WebSocket Forwarding

**Watch Gateway logs:**
```bash
# In Terminal 1 (Gateway)
# You should see:
✅ Alert forwarded: area=centar, level=RED, ...
```

### Test 5: Verify Frontend Connection

**In Flutter console:**
```
Connected to STOMP
```

**In browser DevTools (Network tab):**
- Look for WebSocket connection to `ws://localhost:8081/ws`
- Status should be "101 Switching Protocols"

---

## 🐰 RabbitMQ Management UI

### Access
- **URL**: http://localhost:15672
- **Username**: `weather`
- **Password**: `weather123`

### What to Check

#### **1. Overview Tab**
- Check "Overview" for general stats
- Verify connections (should show 3 - one per service)

#### **2. Queues Tab**
- **agg.readings**: Should have messages coming in
- **gw.alerts**: Should have alerts being consumed
- **dlq.agg.readings**: Should be empty (unless errors)
- **dlq.gw.alerts**: Should be empty (unless errors)

#### **3. Exchanges Tab**
- **readings.topic**: Should show bindings to `agg.readings`
- **alerts.topic**: Should show bindings to `gw.alerts`
- **dlx**: Dead Letter Exchange

#### **4. Messages Tab**
- Click on a queue
- Click "Get messages" to inspect message content
- Useful for debugging

---

## 🔧 Troubleshooting

### Problem: RabbitMQ Not Starting

**Symptoms:**
- `docker ps` shows no rabbitmq container
- Services can't connect

**Solution:**
```bash
# Check Docker is running
docker ps

# Check logs
docker-compose logs rabbitmq

# Restart
docker-compose down
docker-compose up -d

# Wait 15 seconds
sleep 15
```

### Problem: Service Won't Start

**Symptoms:**
- Port already in use error
- Connection refused to RabbitMQ

**Solution:**
```bash
# Check if port is in use
lsof -i :8081  # Gateway
lsof -i :8080  # Aggregator
lsof -i :8082  # Producer

# Kill process if needed
kill -9 <PID>

# Check RabbitMQ is running
docker ps | grep rabbitmq
```

### Problem: No Messages in Queues

**Symptoms:**
- Producer logs show publishing, but queues are empty

**Solution:**
1. Check routing key matches binding pattern
2. Verify exchange exists in RabbitMQ UI
3. Check producer logs for errors
4. Verify RabbitMQ connection in producer logs

### Problem: Messages in Dead Letter Queue

**Symptoms:**
- `dlq.agg.readings` or `dlq.gw.alerts` has messages

**Solution:**
1. **Inspect message in RabbitMQ UI:**
   - Go to Queues → DLQ → Get messages
   - Check message content and headers

2. **Check service logs:**
   - Look for error messages around the time message was sent to DLQ

3. **Common causes:**
   - Invalid message format
   - Processing exception
   - WebSocket connection down (for gateway)

4. **Reprocess message:**
   - In RabbitMQ UI, move message from DLQ back to main queue
   - Or manually requeue via Management API

### Problem: Flutter Can't Connect

**Symptoms:**
- "Connection refused" in Flutter console
- WebSocket connection fails

**Solution:**
1. **Verify Gateway is running:**
   ```bash
   curl http://localhost:8081/actuator/health
   ```

2. **Check WebSocket endpoint:**
   ```bash
   curl http://localhost:8081/ws
   # Should return WebSocket upgrade response
   ```

3. **Check CORS:**
   - Gateway allows all origins (`setAllowedOriginPatterns("*")`)
   - If still issues, check browser console

4. **Verify WebSocket URL in Flutter:**
   - Should be: `ws://localhost:8081/ws`
   - Not: `http://localhost:8081/ws`

### Problem: No Alerts Being Generated

**Symptoms:**
- Readings are received but no alerts published

**Solution:**
1. **Check threshold:**
   - PM10 threshold is 50.0
   - Average must exceed threshold for RED alert

2. **Check window size:**
   - Need at least 1 reading in window
   - Window size is 10 readings

3. **Check alert deduplication:**
   - Alerts only published when level changes
   - If level unchanged, no alert (this is expected)

4. **Check aggregator logs:**
   ```bash
   # Look for:
   📊 No level change: ... (normal if level unchanged)
   🚨 Alert published: ... (when level changes)
   ```

---

## 📈 Monitoring Best Practices

### 1. Regular Health Checks

Set up monitoring to check:
```bash
# Every 30 seconds
watch -n 30 'curl -s http://localhost:8081/actuator/health | jq .status'
```

### 2. Watch Queue Sizes

In RabbitMQ UI, monitor:
- Queue message counts
- Consumer counts
- Message rates

### 3. Check Dead Letter Queues

Regularly check DLQ:
- If messages appear, investigate immediately
- Review service logs for errors

### 4. Monitor Logs

Watch for:
- ❌ Error messages
- ⚠️ Warning messages
- ✅ Success confirmations

---

## 🛑 Stopping the System

### Graceful Shutdown

**Stop Services (in reverse order):**
```bash
# Terminal 4: Flutter
# Press Ctrl+C

# Terminal 3: Producer
# Press Ctrl+C

# Terminal 2: Aggregator
# Press Ctrl+C

# Terminal 1: Gateway
# Press Ctrl+C
```

**Stop RabbitMQ:**
```bash
docker-compose down
```

### Force Stop

```bash
# Kill all Java processes
pkill -f 'spring-boot:run'

# Stop RabbitMQ
docker-compose down
```

---

## 📝 Expected Log Output

### Producer Service (Every 60 seconds)
```
INFO  - Fetched 15 measurements for city SKOPJE
INFO  - ✅ Published measurement: routingKey=reading.centar.pm10, area=centar, city=SKOPJE, metric=pm10, value=45.2, sensor=sensor-123
INFO  - ✅ Published measurement: routingKey=reading.gazi_baba.pm10, area=gazi_baba, city=SKOPJE, metric=pm10, value=52.8, sensor=sensor-456
```

### Aggregator Service
```
INFO  - 📥 Received reading: routingKey=reading.centar.pm10, area=centar, metric=pm10, value=45.2, city=SKOPJE, timestamp=2024-01-15T10:30:00Z
INFO  - 🚨 Alert published: routingKey=alert.centar.RED, area=centar, metric=pm10, level=RED, avg=52.34, windowSize=10
```

### Gateway Service
```
INFO  - ✅ Alert forwarded: area=centar, level=RED, metric=pm10, value=52.34, destinations=[/topic/alerts/centar, /topic/alerts/all]
```

---

## 🎯 Quick Reference

### Service Ports
- **RabbitMQ AMQP**: 5672
- **RabbitMQ Management**: 15672
- **Gateway Service**: 8081
- **Aggregator Service**: 8080
- **Producer Service**: 8082

### RabbitMQ Credentials
- **Username**: `weather`
- **Password**: `weather123`

### Actuator Endpoints
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- RabbitMQ: `/actuator/rabbit`
- Loggers: `/actuator/loggers`

### WebSocket Endpoint
- **URL**: `ws://localhost:8081/ws`
- **Topics**: `/topic/alerts/{area}` and `/topic/alerts/all`

---

## ✅ Success Indicators

Your system is working correctly if you see:

1. ✅ All services show "UP" in health checks
2. ✅ RabbitMQ queues have messages flowing
3. ✅ Producer logs show published measurements
4. ✅ Aggregator logs show received readings and published alerts
5. ✅ Gateway logs show forwarded alerts
6. ✅ Flutter app connects and receives alerts
7. ✅ Map updates with live data

---

## 🎓 Next Steps

1. **Monitor the system** using RabbitMQ UI and Actuator endpoints
2. **Check logs** regularly for any warnings or errors
3. **Inspect Dead Letter Queues** if messages appear
4. **Adjust thresholds** in `AggregatorService` if needed
5. **Scale services** by increasing `max-concurrency` in config

---

**🎉 Your WeatherAlerts system is now running with full RabbitMQ improvements!**

For detailed RabbitMQ concepts and architecture, see `RABBITMQ_IMPROVEMENTS.md`

