package mk.ukim.finki.producerservice.model;

import java.time.Instant;

/**
 * Simple DTO for a sensor reading. This is what we send via RabbitMQ.
 */
public class ReadingMessage {

    private String city;      // e.g. "Skopje"
    private String signal;    // e.g. "pm25"
    private double value;     // numeric value
    private String unit;      // e.g. "µg/m³"
    private Instant timestamp;

    public ReadingMessage() {
    }

    public ReadingMessage(String city, String signal, double value, String unit, Instant timestamp) {
        this.city = city;
        this.signal = signal;
        this.value = value;
        this.unit = unit;
        this.timestamp = timestamp;
    }

    // getters and setters

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}