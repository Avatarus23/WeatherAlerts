package mk.ukim.finki.producerservice.service;

import mk.ukim.finki.producerservice.area.SkopjeAreaResolver;
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

@Component
public class CityProducerScheduler {

    private static final Logger log = LoggerFactory.getLogger(CityProducerScheduler.class);

    private final PulseEcoProperties properties;
    private final PulseEcoClient pulseEcoClient;
    private final MeasurementProducer measurementProducer;
    private final SkopjeAreaResolver skopjeAreaResolver;

    public CityProducerScheduler(PulseEcoProperties properties,
                                 PulseEcoClient pulseEcoClient,
                                 MeasurementProducer measurementProducer,
                                 SkopjeAreaResolver skopjeAreaResolver,
                                 @Value("${producer.poll-interval-ms:60000}") long pollIntervalMs) {
        this.properties = properties;
        this.pulseEcoClient = pulseEcoClient;
        this.measurementProducer = measurementProducer;
        this.skopjeAreaResolver = skopjeAreaResolver;
    }

    @Scheduled(fixedDelayString = "${producer.poll-interval-ms:60000}")
    public void fetchForAllCities() {
        for (String city : properties.getCities()) {
            fetchForCity(city);
        }
    }

    private void fetchForCity(String city) {
        try {
            List<RawDataView> rawList = pulseEcoClient.getCurrentData(city);

            log.info("Fetched {} measurements for city {}", rawList.size(), city.toUpperCase());

            rawList.stream()
                    .map(raw -> toMeasurement(city, raw))
                    .forEach(measurement -> {

                        // Helpful log to verify area dividing works
                        log.info("Publish: city={} area={} pos={} metric={} value={}",
                                measurement.getCity(),
                                measurement.getArea(),
                                measurement.getPosition(),
                                measurement.getMetric(),
                                measurement.getValue());

                        measurementProducer.publishMeasurement(measurement);
                    });

        } catch (HttpClientErrorException e) {
            log.warn("Failed to fetch data for city {}: HTTP {} {}",
                    city.toUpperCase(), e.getStatusCode().value(), e.getStatusText());
        } catch (Exception e) {
            log.error("Unexpected error while fetching data for city {}", city.toUpperCase(), e);
        }
    }

    private CityMeasurement toMeasurement(String city, RawDataView raw) {
        // ---- timestamp ----
        Instant ts;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(raw.getStamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            ts = odt.toInstant();
        } catch (Exception e) {
            ts = Instant.now();
        }

        // ---- value ----
        double value;
        try {
            value = Double.parseDouble(raw.getValue());
        } catch (NumberFormatException ex) {
            value = Double.NaN;
        }

        // ---- resolve area (only for Skopje) ----
        String area = "unknown_area";

        if ("skopje".equalsIgnoreCase(city)) {
            String pos = raw.getPosition(); // "lat,lon"
            if (pos != null && pos.contains(",")) {
                try {
                    String[] parts = pos.split(",");
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    area = skopjeAreaResolver.resolve(lat, lon);
                } catch (Exception ignored) {
                    area = "unknown_area";
                }
            }
        }

        // ---- build measurement ----
        return new CityMeasurement(
                city.toUpperCase(),
                area,
                raw.getSensorId(),
                raw.getPosition(),
                ts,
                raw.getType(),
                value
        );
    }
}
