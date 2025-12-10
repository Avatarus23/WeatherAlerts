package mk.ukim.finki.producerservice.client;

import mk.ukim.finki.weatherproducerservice.model.ReadingMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

@Service
public class PulseEcoClient {

    private final WebClient webClient;

    public PulseEcoClient(@Value("${weather.pulseeco.base-url}") String baseUrl,
                          WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public List<ReadingMessage> fetchLatestSkopje() {
        // TODO: real API call to PulseEco, for now dummy data:
        return List.of(
                new ReadingMessage("Skopje", "pm25", 60.0, "µg/m³", Instant.now()),
                new ReadingMessage("Skopje", "pm10", 80.0, "µg/m³", Instant.now())
        );
    }
}