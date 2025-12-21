package mk.ukim.finki.aggregatorservice.model;

import java.time.Instant;

public class CityMeasurement {
    private String city;      // "Skopje"
    private String area;      // "gazi_baba" (or "Centar")
    private String metric;    // "pm10"
    private Double value;     // 72.5
    private Instant timestamp;

    public CityMeasurement() {}

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
