package mk.ukim.finki.producerservice.pulseeco;

/**
 * Raw Data View - Data Transfer Object (DTO)
 * 
 * This class represents the raw JSON response from pulse.eco API.
 * It matches the structure of the API response exactly.
 * 
 * This is the format we receive from the API, which we then convert
 * to CityMeasurement (our internal format) for processing and publishing.
 * 
 * Example JSON from API:
 * {
 *   "sensorId": "sensor-123",
 *   "position": "41.9981,21.4254",
 *   "stamp": "2024-12-10T21:00:00+01:00",
 *   "year": 2024,
 *   "type": "pm10",
 *   "value": "25.5"
 * }
 */
public class RawDataView {

    private String sensorId;    // "sensor-1, sensor-2, sensor-3"
    private String position;    // "41.9981,21.4254"
    private String stamp;       // "2024-12-10T21:00:00+01:00"
    private Integer year;      // optional
    private String type;       // "pm10", "pm25", "temperature", "humidity", "noise"
    private String value;      // "25.5"

    public RawDataView() {
    }

    //required for JSON deserialization from API response
    public String getSensorId() { return sensorId; }
    public void setSensorId(String sensorId) { this.sensorId = sensorId; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getStamp() { return stamp; }
    public void setStamp(String stamp) { this.stamp = stamp; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}