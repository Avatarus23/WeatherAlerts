package mk.ukim.finki.producerservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
public class SensorView {

    
    private String sensorId;

    
    private String position;

    
    private String comments;

    
    private String type;

    
    private String description;

    
    private String status;
}
