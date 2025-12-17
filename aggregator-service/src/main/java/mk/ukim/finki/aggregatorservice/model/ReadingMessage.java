package mk.ukim.finki.aggregatorservice.model;

import java.time.Instant;

/**
 * Incoming reading message consumed by aggregator.
 *
 * This is compatible with producer-service CityMeasurement JSON.
 */
public class ReadingMessage {
    private String city;
    private String area;
    private String sensorId;
    private String position;
    private Instant timestamp;
    private String metric;
    private double value;

    public ReadingMessage() {}

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
