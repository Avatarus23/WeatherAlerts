# ⚡ Quick Reference Card

## 🚀 Start Everything

```bash
# Option 1: Use script
./start-all.sh

# Option 2: Manual
docker-compose up -d
cd gateway-service && ./mvnw spring-boot:run &
cd aggregator-service && ./mvnw spring-boot:run &
cd producer-service && ./mvnw spring-boot:run &
cd weather_alerts_frontend && flutter run -d chrome
```

## 🛑 Stop Everything

```bash
# Stop services
pkill -f 'spring-boot:run'

# Stop RabbitMQ
docker-compose down
```

## 🔍 Health Checks

```bash
curl http://localhost:8081/actuator/health  # Gateway
curl http://localhost:8080/actuator/health  # Aggregator
curl http://localhost:8082/actuator/health # Producer
```

## 📊 Ports & URLs

| Service | Port | URL |
|---------|------|-----|
| RabbitMQ Management | 15672 | http://localhost:15672 |
| Gateway Service | 8081 | http://localhost:8081 |
| Aggregator Service | 8080 | http://localhost:8080 |
| Producer Service | 8082 | http://localhost:8082 |

## 🐰 RabbitMQ Credentials

- **Username**: `weather`
- **Password**: `weather123`

## 📝 Key Endpoints

### Actuator
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- RabbitMQ: `/actuator/rabbit`
- Loggers: `/actuator/loggers`

### WebSocket
- Endpoint: `ws://localhost:8081/ws`
- Topics: `/topic/alerts/{area}`, `/topic/alerts/all`

## 🔍 RabbitMQ Queues

- `agg.readings` - Aggregator input queue
- `gw.alerts` - Gateway input queue
- `dlq.agg.readings` - Dead letter queue (aggregator)
- `dlq.gw.alerts` - Dead letter queue (gateway)

## 📋 Check Logs

```bash
# View logs
tail -f logs/gateway.log
tail -f logs/aggregator.log
tail -f logs/producer.log

# Or watch service terminals directly
```

## ✅ Success Indicators

- ✅ All health checks return `{"status":"UP"}`
- ✅ RabbitMQ queues show messages
- ✅ Producer logs show: `✅ Published measurement`
- ✅ Aggregator logs show: `📥 Received reading` and `🚨 Alert published`
- ✅ Gateway logs show: `✅ Alert forwarded`
- ✅ Flutter shows: `Connected to STOMP`

## 🐛 Common Issues

| Problem | Solution |
|---------|----------|
| Port in use | `lsof -i :PORT` then `kill -9 PID` |
| RabbitMQ not starting | `docker-compose down && docker-compose up -d` |
| Messages in DLQ | Check service logs, inspect message in RabbitMQ UI |
| Flutter can't connect | Verify Gateway is running on 8081 |

## 📚 Full Documentation

- **Startup Guide**: `STARTUP_GUIDE.md`
- **RabbitMQ Improvements**: `RABBITMQ_IMPROVEMENTS.md`

