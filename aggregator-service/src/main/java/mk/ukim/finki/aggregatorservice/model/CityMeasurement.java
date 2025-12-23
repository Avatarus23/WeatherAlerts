package mk.ukim.finki.aggregatorservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class CityMeasurement {

    private String city;      // "Skopje"
    private String area;      // "gazi_baba" (or "Centar")
    private String metric;    // "pm10"
    private Double value;     // 72.5
    private Instant timestamp;
}
