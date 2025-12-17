package mk.ukim.finki.producerservice.model;

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
public class CityMeasurement {

    private String city;        // e.g. "SKOPJE"
    private String area;        // e.g. "gazi_baba" (normalized key)
    private String sensorId;    // e.g. "sensor-123"
    private String position;    // "lat,lon"
    private Instant timestamp;  // reading timestamp
    private String metric;      // e.g. "pm10"
    private double value;       // numeric value

    public CityMeasurement() {}

    public CityMeasurement(String city, String area, String sensorId, String position, Instant timestamp, String metric, double value) {
        this.city = city;
        this.area = area;
        this.sensorId = sensorId;
        this.position = position;
        this.timestamp = timestamp;
        this.metric = metric;
        this.value = value;
    }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
