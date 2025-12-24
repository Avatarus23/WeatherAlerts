package mk.ukim.finki.producerservice.area;

import org.springframework.stereotype.Component;

@Component
public class SkopjeAreaResolver {

    public String resolve(double lat, double lon) {

        // Bounding box around Skopje (quick reject)
        if (lat < 41.88 || lat > 42.12 || lon < 21.20 || lon > 21.70) {
            return "unknown_area";
        }

        // ✅ Order matters because boxes overlap.
        // We go from more "specific" areas to broader ones.

        // Aerodrom (SE, near airport / south of Vardar)
        if (lat >= 41.93 && lat <= 42.00 && lon >= 21.44 && lon <= 21.54) return "aerodrom";

        // Kisela Voda (S / SE, below Aerodrom)
        if (lat >= 41.88 && lat <= 41.97 && lon >= 21.41 && lon <= 21.56) return "kisela_voda";

        // Centar (central)
        if (lat >= 41.98 && lat <= 42.02 && lon >= 21.40 && lon <= 21.47) return "centar";

        // Čair (north of Centar)
        if (lat >= 42.01 && lat <= 42.06 && lon >= 21.43 && lon <= 21.50) return "cair";

        // Šuto Orizari (north / north-west of Čair)
        if (lat >= 42.05 && lat <= 42.10 && lon >= 21.40 && lon <= 21.50) return "suto_orizari";

        // Butel (north / north-east)
        if (lat >= 42.04 && lat <= 42.10 && lon >= 21.49 && lon <= 21.58) return "butel";

        // Gazi Baba (east / north-east, larger)
        if (lat >= 41.99 && lat <= 42.09 && lon >= 21.50 && lon <= 21.66) return "gazi_baba";

        // Karposh (west-central)
        if (lat >= 41.99 && lat <= 42.07 && lon >= 21.33 && lon <= 21.43) return "karposh";

        // Gjorce Petrov (north-west)
        if (lat >= 42.02 && lat <= 42.12 && lon >= 21.20 && lon <= 21.36) return "gjorce_petrov";

        // Saraj (west / south-west, broad)
        if (lat >= 41.92 && lat <= 42.08 && lon >= 21.20 && lon <= 21.33) return "saraj";

        return "unknown_area";
    }
}
