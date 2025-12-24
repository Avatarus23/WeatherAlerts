package mk.ukim.finki.producerservice.client;

import mk.ukim.finki.producerservice.config.RabbitMQConfig;
import mk.ukim.finki.producerservice.model.CityMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ Message Producer
 *
 * WHAT THIS DOES:
 * - Publishes CityMeasurement objects to RabbitMQ exchange
 * - Uses topic routing keys: reading.{area}.{metric}
 * - Automatically retries on failure (3 attempts with exponential backoff)
 *
 * RABBITMQ PUBLISHING:
 * - convertAndSend() = fire-and-forget (async, no confirmation)
 * - Routing key determines which queues receive the message
 * - Exchange routes message to queues based on bindings
 *
 * Example routing keys:
 *   reading.gazi_baba.pm10
 *   reading.centar.temperature
 *   reading.ohrid.pm25
 */
@Service
public class MeasurementProducer {

    private static final Logger log = LoggerFactory.getLogger(MeasurementProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public MeasurementProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a measurement to RabbitMQ.
     *
     * RETRY MECHANISM:
     * - @Retryable automatically retries on failure
     * - 3 attempts with exponential backoff (1s, 2s, 4s)
     * - If all retries fail, exception is thrown
     *
     * ROUTING KEY FORMAT:
     * - reading.{area}.{metric}
     * - Area and metric are normalized (lowercase, spaces to underscores)
     */
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void publishMeasurement(CityMeasurement measurement) {
        try {
            // Validate and normalize area
            String area = measurement.getArea();
            if (area == null || area.isBlank()) {
                log.warn("Measurement has null/blank area, using 'unknown'");
                area = "unknown";
            }

            // Validate and normalize metric
            String metric = measurement.getMetric();
            if (metric == null || metric.isBlank()) {
                log.warn("Measurement has null/blank metric, using 'unknown'");
                metric = "unknown";
            }

            // Normalize for routing key (lowercase, spaces to underscores)
            String areaKey = area.toLowerCase().replace(" ", "_");
            String metricKey = metric.toLowerCase().replace(" ", "_");

            // Build routing key: reading.{area}.{metric}
            String routingKey = String.format("reading.%s.%s", areaKey, metricKey);

            // Validate value before publishing
            if (Double.isNaN(measurement.getValue()) || Double.isInfinite(measurement.getValue())) {
                log.warn("Skipping measurement with invalid value: area={}, metric={}, value={}",
                        areaKey, metricKey, measurement.getValue());
                return;
            }

            // Publish to RabbitMQ exchange
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    routingKey,
                    measurement
            );

            log.info("✅ Published measurement: routingKey={}, area={}, city={}, metric={}, value={}, sensor={}",
                    routingKey,
                    areaKey,
                    measurement.getCity(),
                    metricKey,
                    measurement.getValue(),
                    measurement.getSensorId()
            );

        } catch (Exception e) {
            log.error("❌ Failed to publish measurement after retries: area={}, city={}, sensorId={}, error={}",
                    measurement.getArea(),
                    measurement.getCity(),
                    measurement.getSensorId(),
                    e.getMessage(),
                    e);
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Failed to publish measurement to RabbitMQ", e);
        }
    }
}
