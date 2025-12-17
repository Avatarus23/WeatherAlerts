package mk.ukim.finki.producerservice.client;

import mk.ukim.finki.producerservice.config.RabbitMQConfig;
import mk.ukim.finki.producerservice.model.CityMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ Message Producer
 *
 * Publishes CityMeasurement to RabbitMQ (JSON) using topic routing keys:
 *   reading.<area>.<metric>
 * Example:
 *   reading.gazi_baba.pm10
 */
@Service
public class MeasurementProducer {

    private static final Logger log = LoggerFactory.getLogger(MeasurementProducer.class);

    private final RabbitTemplate rabbitTemplate;

    public MeasurementProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMeasurement(CityMeasurement measurement) {
        try {
            String area = measurement.getArea();
            if (area == null || area.isBlank()) area = "unknown";

            String metric = measurement.getMetric();
            if (metric == null || metric.isBlank()) metric = "unknown";

            String areaKey = area.toLowerCase().replace(" ", "_");
            String metricKey = metric.toLowerCase().replace(" ", "_");

            String routingKey = String.format("reading.%s.%s", areaKey, metricKey);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,
                    routingKey,
                    measurement
            );

            log.debug("Published measurement: routingKey={} area={} city={} sensor={} metric={} value={}",
                    routingKey,
                    measurement.getArea(),
                    measurement.getCity(),
                    measurement.getSensorId(),
                    measurement.getMetric(),
                    measurement.getValue()
            );

        } catch (Exception e) {
            log.error("Failed to publish measurement for area={} city={} sensorId={}: {}",
                    measurement.getArea(), measurement.getCity(), measurement.getSensorId(), e.getMessage(), e);
        }
    }
}
