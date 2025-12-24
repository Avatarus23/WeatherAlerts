package mk.ukim.finki.aggregatorservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;


@Getter
@Setter
@NoArgsConstructor
public class AlertMessage {

    private String area;
    private String metric;
    private String level;     
    private double value;     
    private double threshold; 
    private Instant timestamp;
    private String reason;
}
