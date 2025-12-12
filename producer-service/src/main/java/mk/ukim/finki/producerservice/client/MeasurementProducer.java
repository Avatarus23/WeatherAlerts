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
 * This service is responsible for publishing measurement data from CityMeasurement to RabbitMQ.
 * Other services (consumers) can subscribe to these messages to process
 * weather/air quality data.
 * 
 * Responsibilities:
 * - Converts CityMeasurement objects to JSON
 * - Publishes messages to RabbitMQ exchange with appropriate routing keys
 * - Handles errors gracefully
 */
@Service
public class MeasurementProducer {

    private static final Logger log = LoggerFactory.getLogger(MeasurementProducer.class);

    private final RabbitTemplate rabbitTemplate;    //rabbitTemplate for sending messaes to RabbitMQ

    public MeasurementProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes a measurement to RabbitMQ
     * 
     * Creates a routing key based on city and metric type, then sends
     * the measurement to the exchange. The message will be automatically
     * routed to the appropriate queue based on the routing key.
     * 
     * Example routing keys:
     * - "measurement.skopje.pm10" (PM10 air quality from Skopje)
     * - "measurement.bitola.temperature" (Temperature from Bitola)
     * - "measurement.ohrid.humidity" (Humidity from Ohrid)
     * 
     * @param measurement The measurement data to publish
     */
    public void publishMeasurement(CityMeasurement measurement) {
        try {
            // Build routing key: "measurement.{city}.{metric}"
            // Example: "measurement.skopje.pm10"
            String routingKey = String.format("measurement.%s.%s", 
                    measurement.getCity().toLowerCase(),  // e.g., "SKOPJE" -> "skopje"
                    measurement.getMetric());            // e.g., "pm10", "temperature"
            
            // Send message to exchange with routing key
            // The message converter will automatically convert CityMeasurement to JSON
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_NAME,  // Exchange name
                    routingKey,                    // Routing key (determines which queue receives it)
                    measurement                    // The actual data (will be converted to JSON)
            );
            
            // Log successful publication (only in debug mode)
            log.debug("Published measurement: city={}, sensor={}, metric={}, value={}",
                    measurement.getCity(),
                    measurement.getSensorId(),
                    measurement.getMetric(),
                    measurement.getValue());
        } catch (Exception e) {
            // Log error but don't crash - allows other measurements to still be published
            log.error("Failed to publish measurement for city {} sensor {}: {}",
                    measurement.getCity(),
                    measurement.getSensorId(),
                    e.getMessage(), e);
        }
    }
}

