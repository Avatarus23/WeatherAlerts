package mk.ukim.finki.producerservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * City Measurement Model
 *
 * Internal data model representing a single sensor measurement.
 * This object is published to RabbitMQ.
 *
 * Key fields used for routing / aggregation:
 *  - city: high-level city name (e.g., "SKOPJE")
 *  - area: sub-area inside the city (e.g., "gazi_baba", "centar")
 *  - metric: pm10/pm25/temperature...
 *  - value: numeric value
 *  - timestamp: when reading was taken
 */
@Getter
@Setter
@NoArgsConstructor
public class CityMeasurement {

    private String city;        // e.g. "SKOPJE"
    private String area;        // e.g. "gazi_baba" (normalized key)
    private String sensorId;    // e.g. "sensor-123"
    private String position;    // "lat,lon"
    private Instant timestamp;  // reading timestamp
    private String metric;      // e.g. "pm10"
    private double value;       // numeric value
}
