package mk.ukim.finki.aggregatorservice.listener;
import mk.ukim.finki.aggregatorservice.config.RabbitConfig;
import mk.ukim.finki.aggregatorservice.model.ReadingMessage;
import mk.ukim.finki.aggregatorservice.service.AggregatorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer for readings.
 *
 * Routing key format:
 *   reading.<area>.<metric>
 *
 * Example:
 *   reading.gazi_baba.pm10
 */
@Component
public class ReadingListener {

    private static final Logger log = LoggerFactory.getLogger(ReadingListener.class);

    private final AggregatorService aggregatorService;

    public ReadingListener(AggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @RabbitListener(queues = RabbitConfig.AGG_QUEUE)
    public void onReading(
            ReadingMessage reading,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey
    ) {
        // expected routing key: reading.<area>.<metric>
        if (routingKey == null) {
            log.warn("Received message without routing key, ignoring");
            return;
        }

        String[] parts = routingKey.split("\\.");
        if (parts.length < 3 || !"reading".equals(parts[0])) {
            log.warn("Ignoring message with unexpected routing key: {}", routingKey);
            return;
        }

        String area = parts[1];
        String metric = parts[2];

        // Fill missing fields from routing key if needed
        if (reading.getArea() == null || reading.getArea().isBlank()) {
            reading.setArea(area);
        }
        if (reading.getMetric() == null || reading.getMetric().isBlank()) {
            reading.setMetric(metric);
        }

        log.info(
                "Received reading: area={}, metric={}, value={}",
                reading.getArea(),
                reading.getMetric(),
                reading.getValue()
        );

        // Pass to aggregator logic
        aggregatorService.process(reading.getArea(), reading.getMetric(), reading);
    }
}