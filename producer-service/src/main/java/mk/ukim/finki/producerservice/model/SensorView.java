package mk.ukim.finki.producerservice.model;

/**
 * Sensor View Model
 * 
 * This model represents sensor metadata/information.
 * Currently not actively used, but available for future features
 * (e.g., getting list of all sensors, sensor details, etc.)
 */
public class SensorView {
    /** Unique sensor identifier */
    private String sensorId;
    
    /** Sensor location/position */
    private String position;
    
    /** Additional comments about the sensor */
    private String comments;
    
    /** Sensor type */
    private String type;
    
    /** Sensor description */
    private String description;
    
    /** Sensor status (e.g., "active", "inactive") */
    private String status;
    public SensorView(String sensorId,
                      String position,
                      String comments,
                      String type,
                      String description,
                      String status) {
        this.sensorId = sensorId;
        this.position = position;
        this.comments = comments;
        this.type = type;
        this.description = description;
        this.status = status;
    }
    public SensorView() {
    }
    public String getSensorId() {
        return sensorId;
    }
    public void setSensorId(String sensorId) {
        this.sensorId = sensorId;
    }
    public String getPosition() {
        return position;
    }
    public void setPosition(String position) {
        this.position = position;
    }
    public String getComments() {
        return comments;
    }
    public void setComments(String comments) {
        this.comments = comments;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
}