package mk.ukim.finki.aggregatorservice.service;

import mk.ukim.finki.aggregatorservice.config.RabbitConfig;
import mk.ukim.finki.aggregatorservice.model.AlertMessage;
import mk.ukim.finki.aggregatorservice.model.ReadingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Business logic: aggregates readings and publishes alerts.
 *
 * Listener is in ReadingListener.java.
 */
@Service
public class AggregatorService {

    private final RabbitTemplate rabbitTemplate;

    // Window per (area|metric), last N values
    private final Map<String, Deque<Double>> windows = new HashMap<>();
    private static final int WINDOW_SIZE = 10;

    public AggregatorService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

// add this field at the top of the class (under windows map)
private final Map<String, String> lastLevelByKey = new HashMap<>();

public void process(String area, String metric, ReadingMessage reading) {
    if (area == null || area.isBlank()) area = "unknown";
    if (metric == null || metric.isBlank()) metric = "unknown";

    String key = area + "|" + metric;

    Deque<Double> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
    window.addLast(reading.getValue());
    while (window.size() > WINDOW_SIZE) window.removeFirst();

    double avg = window.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

    // ✅ Your requirement: pm10 limit => red else green
    // You can later extend to pm25/uv/etc.
    String level;
    double threshold;

    if ("pm10".equalsIgnoreCase(metric)) {
        threshold = 50.0;
        level = (avg > threshold) ? "RED" : "GREEN";
    } else {
        // default behavior for other metrics (keep it simple)
        threshold = 0.0;
        level = "GREEN";
    }

    String last = lastLevelByKey.get(key);

    // ✅ publish only when state changes (including back to GREEN)
    if (last == null || !last.equals(level)) {
        lastLevelByKey.put(key, level);

        AlertMessage alert = new AlertMessage();
        alert.setArea(area);
        alert.setMetric(metric);
        alert.setLevel(level);
        alert.setValue(avg);
        alert.setThreshold(threshold);
        alert.setTimestamp(Instant.now());
        alert.setReason("Avg " + metric + " over last " + window.size() + " readings = " + avg);

        String routingKey = "alert." + area + "." + level;

        rabbitTemplate.convertAndSend(RabbitConfig.ALERTS_EXCHANGE, routingKey, alert);

        System.out.println("[AGGREGATOR] Sent alert: routingKey=" + routingKey + " level=" + level + " avg=" + avg);
    } else {
        System.out.println("[AGGREGATOR] No change: area=" + area + " metric=" + metric + " level=" + level + " avg=" + avg);
    }
}

}
