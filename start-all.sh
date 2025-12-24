#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  WeatherAlerts System Startup${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: Start RabbitMQ
echo -e "${YELLOW}🐰 Step 1: Starting RabbitMQ...${NC}"
docker-compose up -d

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start RabbitMQ${NC}"
    exit 1
fi

# Wait for RabbitMQ to be ready
echo -e "${YELLOW}⏳ Waiting for RabbitMQ to be ready...${NC}"
sleep 15

# Check if RabbitMQ is responding
echo -e "${YELLOW}🔍 Checking RabbitMQ health...${NC}"
for i in {1..10}; do
    if curl -s -u weather:weather123 http://localhost:15672/api/overview > /dev/null 2>&1; then
        echo -e "${GREEN}✅ RabbitMQ is ready!${NC}"
        break
    fi
    if [ $i -eq 10 ]; then
        echo -e "${RED}❌ RabbitMQ failed to start after 10 attempts${NC}"
        exit 1
    fi
    sleep 2
done

echo ""
echo -e "${BLUE}🚀 Step 2: Starting Spring Boot Services...${NC}"
echo ""

# Start Gateway Service
echo -e "${YELLOW}📡 Starting Gateway Service (port 8081)...${NC}"
(cd gateway-service && ./mvnw spring-boot:run > ../logs/gateway.log 2>&1) &
GATEWAY_PID=$!
echo -e "${GREEN}   Gateway Service PID: $GATEWAY_PID${NC}"

# Start Aggregator Service
echo -e "${YELLOW}📊 Starting Aggregator Service (port 8080)...${NC}"
(cd aggregator-service && ./mvnw spring-boot:run > ../logs/aggregator.log 2>&1) &
AGGREGATOR_PID=$!
echo -e "${GREEN}   Aggregator Service PID: $AGGREGATOR_PID${NC}"

# Start Producer Service
echo -e "${YELLOW}📥 Starting Producer Service (port 8082)...${NC}"
(cd producer-service && ./mvnw spring-boot:run > ../logs/producer.log 2>&1) &
PRODUCER_PID=$!
echo -e "${GREEN}   Producer Service PID: $PRODUCER_PID${NC}"

# Create logs directory if it doesn't exist
mkdir -p logs

# Wait a bit for services to start
echo ""
echo -e "${YELLOW}⏳ Waiting for services to initialize (15 seconds)...${NC}"
sleep 15

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✅ All backend services are starting!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "${YELLOW}Service Status:${NC}"
echo -e "  🐰 RabbitMQ:     http://localhost:15672 (weather/weather123)"
echo -e "  📡 Gateway:      http://localhost:8081"
echo -e "  📊 Aggregator:   http://localhost:8080"
echo -e "  📥 Producer:     http://localhost:8082"
echo ""
echo -e "${YELLOW}Health Checks:${NC}"
echo -e "  curl http://localhost:8081/actuator/health"
echo -e "  curl http://localhost:8080/actuator/health"
echo -e "  curl http://localhost:8082/actuator/health"
echo ""
echo -e "${YELLOW}Logs:${NC}"
echo -e "  tail -f logs/gateway.log"
echo -e "  tail -f logs/aggregator.log"
echo -e "  tail -f logs/producer.log"
echo ""
echo -e "${YELLOW}📱 To start Flutter frontend:${NC}"
echo -e "  cd weather_alerts_frontend"
echo -e "  flutter pub get"
echo -e "  flutter run -d chrome"
echo ""
echo -e "${YELLOW}🛑 To stop all services:${NC}"
echo -e "  kill $GATEWAY_PID $AGGREGATOR_PID $PRODUCER_PID"
echo -e "  docker-compose down"
echo ""
echo -e "${GREEN}✨ System is starting up! Check logs for details.${NC}"