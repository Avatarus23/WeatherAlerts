# RabbitMQ Improvements & Architecture

## 📋 Overview

This document explains all the improvements made to the WeatherAlerts project, with a focus on RabbitMQ best practices and architecture.

---

## 🎯 What Was Improved

### 1. **RabbitMQ Configuration Enhancements**

#### **Dead Letter Queues (DLQ)**
- **What it is**: A special queue that receives messages that failed processing after all retries
- **Why it's important**: Prevents message loss and allows manual inspection/reprocessing
- **How it works**:
  - When a message fails processing, it's rejected
  - After max retries (3), message is sent to DLQ instead of being lost
  - You can inspect DLQ in RabbitMQ Management UI and manually reprocess messages

**Implementation**:
- Each service has its own DLQ: `dlq.agg.readings` and `dlq.gw.alerts`
- Configured via queue arguments: `x-dead-letter-exchange` and `x-dead-letter-routing-key`

#### **Publisher Confirms**
- **What it is**: RabbitMQ confirms that it received your message
- **Why it's important**: Ensures messages aren't lost during transmission
- **How it works**:
  - Producer sends message to exchange
  - RabbitMQ sends confirmation back
  - If no confirmation, message might be lost (you can retry)

**Implementation**:
- Enabled via `publisher-confirm-type: correlated`
- Callbacks log success/failure

#### **Message TTL (Time To Live)**
- **What it is**: Messages older than 60 seconds are automatically discarded
- **Why it's important**: Prevents queue from filling with stale data
- **Configuration**: `x-message-ttl: 60000` (milliseconds)

#### **Retry Mechanism**
- **What it is**: Automatic retry of failed operations
- **Why it's important**: Handles transient failures (network issues, temporary unavailability)
- **How it works**:
  - 3 attempts with exponential backoff (1s, 2s, 4s)
  - If all retries fail, message goes to DLQ

**Implementation**:
- Spring AMQP retry (for consumers)
- Spring Retry with `@Retryable` (for producers)

---

## 🏗️ RabbitMQ Architecture

### **Exchange Types**

#### **Topic Exchange** (Used in this project)
- Routes messages based on routing key patterns
- Patterns use wildcards: `*` (one word) and `#` (zero or more words)

**Examples**:
- `reading.#` → matches: `reading.centar.pm10`, `reading.gazi_baba.temperature`, etc.
- `alert.*.*` → matches: `alert.centar.RED`, `alert.gazi_baba.YELLOW`, etc.
- `reading.*.pm10` → matches only PM10 readings: `reading.centar.pm10`, `reading.ohrid.pm10`

### **Message Flow**

```
┌─────────────────┐
│ Producer        │
│ Service         │
└────────┬────────┘
         │
         │ Publishing to exchange
         │ routingKey: "reading.area.metric"
         ▼
┌─────────────────────────────────┐
│ Exchange: readings.topic         │
│ (Topic Exchange)                 │
└────────┬─────────────────────────┘
         │
         │ Routing based on bindings
         ▼
┌─────────────────────────────────┐
│ Queue: agg.readings             │
│ Binding: reading.#               │
│ (Receives ALL readings)          │
└────────┬─────────────────────────┘
         │
         │ Consumer processes message
         ▼
┌─────────────────┐
│ Aggregator      │
│ Service         │
└────────┬────────┘
         │
         │ Publishes alert
         │ routingKey: "alert.area.level"
         ▼
┌─────────────────────────────────┐
│ Exchange: alerts.topic           │
└────────┬─────────────────────────┘
         │
         │ Routing
         ▼
┌─────────────────────────────────┐
│ Queue: gw.alerts                │
│ Binding: alert.*.*              │
│ (Receives ALL alerts)           │
└────────┬─────────────────────────┘
         │
         │ Consumer forwards to WebSocket
         ▼
┌─────────────────┐
│ Gateway         │
│ Service         │
└─────────────────┘
```

---

## 📝 Configuration Files Explained

### **application.yml / application.properties**

#### **Connection Settings**
```yaml
spring.rabbitmq:
  host: ${RABBITMQ_HOST:localhost}  # Environment variable with default
  port: ${RABBITMQ_PORT:5672}
  username: ${RABBITMQ_USER:weather}
  password: ${RABBITMQ_PASS:weather123}
```
- **Environment variables**: Allows different configs for dev/staging/prod
- **Default values**: Fallback if env vars not set

#### **Publisher Settings**
```yaml
publisher-confirm-type: correlated  # Enable confirms
publisher-returns: true            # Get notified if message can't be routed
```

#### **Listener Settings**
```yaml
listener:
  simple:
    acknowledge-mode: auto          # Auto-ack after successful processing
    retry:
      enabled: true
      max-attempts: 3               # Retry 3 times
      initial-interval: 1000ms      # Wait 1s before first retry
      multiplier: 2.0               # Double wait time each retry
    prefetch: 10                    # Max unacknowledged messages per consumer
    concurrency: 1                  # Start with 1 consumer
    max-concurrency: 5              # Scale up to 5 consumers if needed
```

---

## 🔧 Service-Specific Improvements

### **Producer Service**

#### **MeasurementProducer**
- **Retry mechanism**: `@Retryable` with 3 attempts
- **Validation**: Checks for null/invalid values before publishing
- **Logging**: Detailed logs with emojis for easy reading
- **Error handling**: Re-throws exceptions to trigger retry

#### **RabbitMQConfig**
- **Publisher confirms**: Logs when messages are confirmed
- **Return callbacks**: Logs when messages can't be routed
- **Mandatory flag**: Messages must be routed to at least one queue

### **Aggregator Service**

#### **ReadingListener**
- **Validation**: Validates routing key format and message data
- **Error handling**: Rejects invalid messages (goes to DLQ)
- **Logging**: Detailed logs for debugging

#### **AggregatorService**
- **Sliding window**: Maintains last 10 readings per area+metric
- **Alert deduplication**: Only publishes when alert level changes
- **Thread safety**: Uses `ConcurrentHashMap` for multi-threaded safety
- **Logging**: Clear logs showing aggregation results

#### **RabbitConfig**
- **DLQ setup**: Dead Letter Exchange and Queue
- **Message TTL**: 60 seconds
- **Listener factory**: Configured to send failed messages to DLQ

### **Gateway Service**

#### **AlertForwarder**
- **Dual forwarding**: Sends to area-specific topic AND broadcast topic
- **Error handling**: Failed WebSocket forwarding goes to DLQ
- **Logging**: Tracks all forwarded alerts

#### **RabbitConfig**
- **DLQ setup**: For failed WebSocket forwarding
- **Listener factory**: Proper error handling configuration

---

## 📊 Monitoring & Observability

### **Actuator Endpoints**

Access these endpoints for monitoring:

#### **Health Check**
```
GET http://localhost:8081/actuator/health
GET http://localhost:8080/actuator/health
GET http://localhost:8082/actuator/health
```
- Shows service health status
- Includes RabbitMQ connection status

#### **Metrics**
```
GET http://localhost:8081/actuator/metrics
```
- Shows all available metrics
- Includes JVM, HTTP, and custom metrics

#### **RabbitMQ Info**
```
GET http://localhost:8081/actuator/rabbit
```
- Shows RabbitMQ connection details
- Queue information

#### **Loggers**
```
GET http://localhost:8081/actuator/loggers
```
- View and modify log levels at runtime

---

## 🐰 RabbitMQ Management UI

Access at: **http://localhost:15672**

**Login**: `weather` / `weather123`

### **What You Can Do**:
1. **View Queues**: See message counts, consumers, etc.
2. **Inspect Messages**: Look at message content in queues
3. **View Exchanges**: See all exchanges and their bindings
4. **Monitor Connections**: See active connections
5. **Check DLQ**: Inspect failed messages in Dead Letter Queues

---

## 🔍 Key RabbitMQ Concepts Explained

### **Exchange**
- **Purpose**: Routes messages to queues based on routing keys
- **Types**: Direct, Topic, Fanout, Headers
- **In this project**: Topic Exchange (pattern matching)

### **Queue**
- **Purpose**: Stores messages until consumed
- **Durable**: Survives broker restarts
- **Exclusive**: Only one connection can use it
- **Auto-delete**: Deleted when no longer used

### **Binding**
- **Purpose**: Links queue to exchange with routing pattern
- **Example**: Queue `agg.readings` bound to `readings.topic` with pattern `reading.#`

### **Routing Key**
- **Purpose**: Determines which queues receive the message
- **Format**: Dot-separated words (e.g., `reading.gazi_baba.pm10`)
- **Pattern matching**: `*` = one word, `#` = zero or more words

### **Publisher Confirms**
- **Purpose**: Ensures message was received by exchange
- **When to use**: When you need guaranteed delivery

### **Dead Letter Queue**
- **Purpose**: Stores messages that failed processing
- **When used**: After max retries exceeded
- **Benefit**: Allows manual inspection and reprocessing

### **Message TTL**
- **Purpose**: Automatically discards old messages
- **Benefit**: Prevents queue overflow with stale data

### **Prefetch**
- **Purpose**: Limits unacknowledged messages per consumer
- **Benefit**: Prevents one slow consumer from hogging all messages
- **Default**: 10 messages

---

## 🚀 Best Practices Implemented

1. ✅ **Dead Letter Queues** - No message loss
2. ✅ **Publisher Confirms** - Guaranteed delivery
3. ✅ **Retry Mechanisms** - Handles transient failures
4. ✅ **Message Validation** - Prevents bad data
5. ✅ **Proper Logging** - Easy debugging
6. ✅ **Environment Variables** - Flexible configuration
7. ✅ **Thread Safety** - ConcurrentHashMap for shared state
8. ✅ **Alert Deduplication** - Prevents spam
9. ✅ **Message TTL** - Prevents queue overflow
10. ✅ **Actuator Monitoring** - Health checks and metrics

---

## 📖 How to Use

### **Starting the System**

1. **Start RabbitMQ**:
   ```bash
   docker-compose up -d
   ```

2. **Start Services** (in order):
   ```bash
   # Terminal 1: Gateway
   cd gateway-service && ./mvnw spring-boot:run
   
   # Terminal 2: Aggregator
   cd aggregator-service && ./mvnw spring-boot:run
   
   # Terminal 3: Producer
   cd producer-service && ./mvnw spring-boot:run
   ```

3. **Check Health**:
   ```bash
   curl http://localhost:8081/actuator/health
   curl http://localhost:8080/actuator/health
   curl http://localhost:8082/actuator/health
   ```

### **Monitoring**

- **RabbitMQ UI**: http://localhost:15672
- **Actuator**: http://localhost:8081/actuator
- **Check DLQ**: In RabbitMQ UI, look for queues starting with `dlq.`

### **Troubleshooting**

1. **Messages not being consumed**:
   - Check queue bindings in RabbitMQ UI
   - Verify routing key matches binding pattern
   - Check consumer logs for errors

2. **Messages in DLQ**:
   - Inspect message content in RabbitMQ UI
   - Check service logs for error details
   - Manually requeue if needed

3. **Connection issues**:
   - Verify RabbitMQ is running: `docker ps`
   - Check connection settings in application.yml
   - Review connection logs

---

## 🎓 Learning Resources

- **RabbitMQ Tutorials**: https://www.rabbitmq.com/getstarted.html
- **Spring AMQP Docs**: https://spring.io/projects/spring-amqp
- **Topic Exchange Patterns**: https://www.rabbitmq.com/tutorials/tutorial-five-java.html

---

## 📝 Summary

All improvements focus on:
- **Reliability**: No message loss (DLQ, confirms, retries)
- **Observability**: Logging, monitoring, health checks
- **Resilience**: Error handling, validation, retries
- **Best Practices**: Proper RabbitMQ configuration and patterns

The system is now production-ready with proper error handling, monitoring, and RabbitMQ best practices! 🎉

