package mk.ukim.finki.producerservice.area;

import org.springframework.stereotype.Component;

@Component
public class SkopjeAreaResolver {

    public String resolve(double lat, double lon) {

        // DEMO boxes (you will replace with real borders later)
        if (lat >= 42.000 && lat <= 42.020 && lon >= 21.430 && lon <= 21.470) return "centar";
        if (lat >= 42.000 && lat <= 42.060 && lon >= 21.480 && lon <= 21.540) return "gazi_baba";
        if (lat >= 42.010 && lat <= 42.060 && lon >= 21.380 && lon <= 21.430) return "karposh";

        return "unknown_area";
    }
}
