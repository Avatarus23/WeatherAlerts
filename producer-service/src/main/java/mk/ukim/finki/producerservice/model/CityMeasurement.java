package mk.ukim.finki.producerservice.model;

import java.time.Instant;

/**
 * City Measurement Model
 * 
 * This is our internal data model representing a single sensor measurement.
 * It's used throughout the application and is what we publish to RabbitMQ.
 * 
 * Example:
 * - city: "SKOPJE"
 * - sensorId: "sensor-123"
 * - position: "41.9981,21.4254" (latitude,longitude)
 * - timestamp: 2024-12-10T21:00:00Z
 * - metric: "pm10" (or "temperature", "humidity", "pm25", etc.)
 * - value: 25.5
 */
public class CityMeasurement {

    private String city;    // "SKOPJE", "BITOLA"
    private String sensorId;    // "sensor-123"
    private String position;    // "41.9981,21.4254"
    private Instant timestamp;   // "2024-12-10T21:00:00Z"
    private String metric;      // "pm10", "pm25", "temperature", "humidity", "noise", etc.
    private double value;       // 25.5

    public CityMeasurement() { }

    public CityMeasurement(String city, String sensorId, String position,
                           Instant timestamp, String metric, double value) {
        this.city = city;
        this.sensorId = sensorId;
        this.position = position;
        this.timestamp = timestamp;
        this.metric = metric;
        this.value = value;
    }

    // Getters and setters (required for JSON serialization/deserialization)
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

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