package mk.ukim.finki.aggregatorservice.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import mk.ukim.finki.aggregatorservice.config.RabbitConfig;
import mk.ukim.finki.aggregatorservice.model.ReadingMessage;
import mk.ukim.finki.aggregatorservice.service.AggregatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for readings.
 *
 * Reads routing key: reading.<area>.<metric>
 * Parses it and passes it to AggregatorService.process(...).
 */
@Component
public class ReadingListener {

    private static final Logger log = LoggerFactory.getLogger(ReadingListener.class);

    private final ObjectMapper objectMapper;
    private final AggregatorService aggregatorService;

    public ReadingListener(ObjectMapper objectMapper, AggregatorService aggregatorService) {
        this.objectMapper = objectMapper;
        this.aggregatorService = aggregatorService;
    }

    @RabbitListener(queues = RabbitConfig.AGG_QUEUE)
    public void onReading(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        // expected: reading.<area>.<metric>
        String[] parts = routingKey == null ? new String[0] : routingKey.split("\\.");
        if (parts.length < 3 || !"reading".equals(parts[0])) {
            log.warn("Ignoring message with unexpected routing key: {}", routingKey);
            return;
        }

        String area = parts[1];
        String metric = parts[2];

        try {
            ReadingMessage reading = objectMapper.readValue(message.getBody(), ReadingMessage.class);

            // If body doesn't contain area/metric yet, fill from routing key
            if (reading.getArea() == null || reading.getArea().isBlank()) reading.setArea(area);
            if (reading.getMetric() == null || reading.getMetric().isBlank()) reading.setMetric(metric);

            log.info("Received reading: area={}, metric={}, value={}", area, metric, reading.getValue());

            aggregatorService.process(area, metric, reading);

        } catch (Exception e) {
            log.error("Failed to parse/process reading. routingKey={}", routingKey, e);
        }
    }
}
