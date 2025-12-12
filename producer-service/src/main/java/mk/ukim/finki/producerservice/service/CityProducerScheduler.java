package mk.ukim.finki.producerservice.service;

import mk.ukim.finki.producerservice.client.MeasurementProducer;
import mk.ukim.finki.producerservice.config.PulseEcoProperties;
import mk.ukim.finki.producerservice.model.CityMeasurement;
import mk.ukim.finki.producerservice.pulseeco.PulseEcoClient;
import mk.ukim.finki.producerservice.pulseeco.RawDataView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Scheduled Service for Fetching and Publishing City Measurements
// - Periodically fetches data from pulse.eco APIs for configured cities
// - The scheduling is configured via @Scheduled annotation and runs every X milliseconds as specified in application.properties
// - Converts RawDataView data to our internal CityMeasurement format
// - Publishes each measurement to RabbitMQ for other services to consume

@Component
public class CityProducerScheduler {

    private static final Logger log = LoggerFactory.getLogger(CityProducerScheduler.class);

    // pulse.eco properties
    private final PulseEcoProperties properties;
    
    // Client for making API calls to pulse.eco
    private final PulseEcoClient pulseEcoClient;
    
    // Producer for publishing messages to RabbitMQ
    private final MeasurementProducer measurementProducer;

    /**
     * Constructor - Spring automatically injects all dependencies
     * 
     * @param properties Configuration with cities list
     * @param pulseEcoClient Client for pulse.eco API
     * @param measurementProducer RabbitMQ message publisher
     * @param pollIntervalMs How often to fetch data (from application.properties, default: 60000ms)
     */
    public CityProducerScheduler(PulseEcoProperties properties,
                                 PulseEcoClient pulseEcoClient,
                                 MeasurementProducer measurementProducer,
                                 @Value("${producer.poll-interval-ms:60000}") long pollIntervalMs) {
        this.properties = properties;
        this.pulseEcoClient = pulseEcoClient;
        this.measurementProducer = measurementProducer;
    }

    /**
     * Scheduled method that runs periodically
     * 
     * This method is automatically called by Spring at fixed intervals.
     * It loops through all cities configured in application.properties
     * and fetches data for each one.
     * 
     * The interval is configured via: producer.poll-interval-ms
     * 
     * fixedDelayString means: "Wait X milliseconds AFTER the previous execution finishes"
     * This ensures we don't overlap executions if one takes longer than expected.
     */
    @Scheduled(fixedDelayString = "${producer.poll-interval-ms:60000}")
    public void fetchForAllCities() {
        // Iterate through all cities from application.properties
        for (String city : properties.getCities()) {
            fetchForCity(city);
        }
    }

    /**
     * Fetches data for a specific city and publishes to RabbitMQ
     * 
     * Process:
     * 1. Call pulse.eco API to get current measurements for the city
     * 2. Convert each raw measurement to CityMeasurement format
     * 3. Publish each measurement to RabbitMQ
     * 
     * @param city City name (e.g., "skopje", "bitola")
     */
    private void fetchForCity(String city) {
        try {
            // Step 1: Fetch raw data from pulse.eco API
            // This returns a list of RawDataView objects (one per sensor reading)
            List<RawDataView> rawList = pulseEcoClient.getCurrentData(city);

            log.info("Fetched {} measurements for city {}", rawList.size(), city.toUpperCase());

            // Step 2: Process each raw measurement
            rawList.stream()
                    // Convert RawDataView to CityMeasurement (our internal format)
                    .map(raw -> toMeasurement(city, raw))
                    // For each measurement:
                    .forEach(measurement -> {
                        // Log the measurement details (only in debug mode)
                        log.debug("Measurement: city={} sensor={} type={} value={} timestamp={}",
                                measurement.getCity(),
                                measurement.getSensorId(),
                                measurement.getMetric(),
                                measurement.getValue(),
                                measurement.getTimestamp());
                        
                        // Step 3: Publish to RabbitMQ
                        // This sends the measurement to the message queue for other services
                        measurementProducer.publishMeasurement(measurement);
                    });
        } catch (HttpClientErrorException e) {
            // Handle HTTP errors (e.g., 404, 401, 500) gracefully
            // Log warning but continue processing other cities
            log.warn("Failed to fetch data for city {}: HTTP {} {}",
                    city.toUpperCase(), e.getStatusCode().value(), e.getStatusText());
        } catch (Exception e) {
            // Handle any other unexpected errors
            log.error("Unexpected error while fetching data for city {}", city.toUpperCase(), e);
        }
    }

    /**
     * Converts RawDataView (from pulse.eco API) to CityMeasurement (our internal format)
     * 
     * This method handles:
     * - Parsing timestamp strings to Instant objects
     * - Converting string values to numeric values
     * - Handling parsing errors gracefully (uses defaults if parsing fails)
     * 
     * @param city City name (e.g., "skopje")
     * @param raw Raw data from pulse.eco API
     * @return CityMeasurement object ready for publishing
     */
    private CityMeasurement toMeasurement(String city, RawDataView raw) {
        // Parse timestamp from ISO-8601 format (e.g., "2024-12-10T21:00:00+01:00")
        Instant ts;
        try {
            // The stamp field contains ISO-8601 datetime with timezone offset
            // Example: "2024-12-10T21:00:00+01:00"
            OffsetDateTime odt = OffsetDateTime.parse(raw.getStamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            ts = odt.toInstant();  // Convert to UTC Instant
        } catch (Exception e) {
            // If parsing fails (malformed date), use current time as fallback
            ts = Instant.now();
        }

        // Parse numeric value from string
        double value;
        try {
            // Convert string value (e.g., "25.5") to double
            value = Double.parseDouble(raw.getValue());
        } catch (NumberFormatException ex) {
            // If parsing fails (e.g., "N/A", empty string), use NaN (Not a Number)
            value = Double.NaN;
        }

        // Create and return CityMeasurement object
        return new CityMeasurement(
                city.toUpperCase(),      // City name in uppercase (e.g., "SKOPJE")
                raw.getSensorId(),       // Sensor identifier
                raw.getPosition(),       // Location/position (e.g., coordinates)
                ts,                      // Timestamp (parsed or current time)
                raw.getType(),           // Metric type (e.g., "pm10", "temperature", "humidity")
                value                    // Numeric value (or NaN if invalid)
        );
    }
}