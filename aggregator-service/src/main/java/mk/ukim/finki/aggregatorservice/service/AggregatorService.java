package mk.ukim.finki.aggregatorservice.service;

import mk.ukim.finki.aggregatorservice.config.RabbitConfig;
import mk.ukim.finki.aggregatorservice.model.AlertMessage;
import mk.ukim.finki.aggregatorservice.model.ReadingMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Listens to readings from agg.Skopje queue and publishes alerts
 * to alerts.topic when thresholds are exceeded.
 */
@Service
public class AggregatorService {

    private final RabbitTemplate rabbitTemplate;

    // For simplicity: window per signal, last N readings
    private final Map<String, Deque<ReadingMessage>> windows = new HashMap<>();
    private static final int WINDOW_SIZE = 10; // keep last 10 values

    public AggregatorService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitConfig.AGG_QUEUE)
    public void onReading(ReadingMessage reading) {
        String signal = reading.getSignal();

        // Get or create a window for this signal
        Deque<ReadingMessage> window =
                windows.computeIfAbsent(signal, s -> new ArrayDeque<>());

        window.addLast(reading);

        // If window is too big, remove oldest
        while (window.size() > WINDOW_SIZE) {
            window.removeFirst();
        }

        // Compute simple average
        double avg = window.stream()
                .mapToDouble(ReadingMessage::getValue)
                .average()
                .orElse(0.0);

        // Very simple threshold logic for pm25
        String level;
        double threshold;

        if (avg < 25) {
            level = "GREEN";
            threshold = 25;
        } else if (avg < 75) {
            level = "YELLOW";
            threshold = 75;
        } else {
            level = "RED";
            threshold = 75;
        }

        // For test, we send alerts only when not GREEN
        if (!"GREEN".equals(level)) {
            AlertMessage alert = new AlertMessage();
            alert.setCity(reading.getCity());
            alert.setSignal(signal);
            alert.setLevel(level);
            alert.setValue(avg);
            alert.setThreshold(threshold);
            alert.setTimestamp(Instant.now());
            alert.setReason("Average " + signal + " over last " + window.size() + " readings = " + avg);

            String routingKey = "alert." + alert.getCity() + "." + alert.getLevel();

            rabbitTemplate.convertAndSend(
                    RabbitConfig.ALERTS_EXCHANGE,
                    routingKey,
                    alert
            );

            System.out.println("[AGGREGATOR] Sent alert: " + level + " avg=" + avg);
        } else {
            System.out.println("[AGGREGATOR] Level GREEN avg=" + avg);
        }
    }
}