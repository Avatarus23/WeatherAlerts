package mk.ukim.finki.gatewayservice.service;

import mk.ukim.finki.gatewayservice.config.RabbitConfig;
import mk.ukim.finki.gatewayservice.model.AlertMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Forwards alerts from RabbitMQ to WebSocket topic /topic/alerts/<City>.
 */
@Service
public class AlertForwarder {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertForwarder(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = RabbitConfig.GW_ALERTS_QUEUE)
    public void onAlert(AlertMessage alert) {
        String destination = "/topic/alerts/" + alert.getCity();
        messagingTemplate.convertAndSend(destination, alert);
        System.out.println("[GATEWAY] Forwarded alert to " + destination + ": " + alert.getLevel());
    }
}