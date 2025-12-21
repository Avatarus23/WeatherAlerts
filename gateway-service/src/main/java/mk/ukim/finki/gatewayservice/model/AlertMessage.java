package mk.ukim.finki.gatewayservice.model;

import java.time.Instant;

/**
 * Alert DTO received from RabbitMQ and forwarded to WebSocket.
 * Must match aggregator-service AlertMessage JSON.
 */
public class AlertMessage {
    private String area;
    private String metric;
    private String level;
    private double value;
    private double threshold;
    private Instant timestamp;
    private String reason;

    public AlertMessage() {}

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public double getThreshold() { return threshold; }
    public void setThreshold(double threshold) { this.threshold = threshold; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
// Convenience fields for frontend (Flutter)

public String getAreaKey() {
    if (area == null) return "unknown";
    return area.toLowerCase().replace(" ", "_");
}

public boolean isAlarm() {
    return level != null && !"GREEN".equalsIgnoreCase(level);
}

// Flutter-friendly: if metric is pm10, expose value as pm10
public Double getPm10() {
    if (metric != null && metric.equalsIgnoreCase("pm10")) return value;
    return null;
}
}
