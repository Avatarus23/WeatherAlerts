package mk.ukim.finki.gatewayservice.service;

import mk.ukim.finki.gatewayservice.config.RabbitConfig;
import mk.ukim.finki.gatewayservice.model.AlertMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Forwards alerts from RabbitMQ to WebSocket topics:
 *   /topic/alerts/<area>
 *
 * Example: /topic/alerts/gazi_baba
 */
@Service
public class AlertForwarder {

    private final SimpMessagingTemplate messagingTemplate;

    public AlertForwarder(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(queues = RabbitConfig.GW_ALERTS_QUEUE)
    public void onAlert(AlertMessage alert) {
    String area = alert.getArea();
    if (area == null || area.isBlank()) area = "unknown";

    String areaKey = area.toLowerCase().replace(" ", "_");

    String destinationArea = "/topic/alerts/" + areaKey;
    String destinationAll  = "/topic/alerts/all";

    messagingTemplate.convertAndSend(destinationArea, alert);
    messagingTemplate.convertAndSend(destinationAll, alert);

    System.out.println("[GATEWAY] Forwarded alert to " + destinationArea + " and " + destinationAll +
            ": " + alert.getLevel());
}
}
