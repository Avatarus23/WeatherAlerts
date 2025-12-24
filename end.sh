#!/bin/bash
# Script to stop all services and RabbitMQ
echo ""
echo -e "${BLUE}🛑 Stopping all services...${NC}"
pkill -f 'spring-boot:run'
echo -e "${BLUE}🛑 Stopping RabbitMQ...${NC}
docker-compose down
echo -e "${GREEN}✅ All services and RabbitMQ stopped successfully.${NC}"