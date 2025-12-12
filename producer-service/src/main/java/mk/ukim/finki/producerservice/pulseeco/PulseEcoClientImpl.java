package mk.ukim.finki.producerservice.pulseeco;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

// makes HTTP req to pulse.eco to recieve env data
// RestTemplate which is configured with Basic Authentication in RestTemplateConfig.

// API Endpoints:
// https://skopje.pulse.eco/rest/current
// https://bitola.pulse.eco/rest/current
// https://ohrid.pulse.eco/rest/current

@Service
public class PulseEcoClientImpl implements PulseEcoClient {

    /** RestTemplate is configured with authentication in RestTemplateConfig */
    private final RestTemplate restTemplate;

    /**
     * Constructor - Spring automatically injects RestTemplate
     */
    public PulseEcoClientImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Builds the base URL for a city's pulse.eco API
     * 
     * @param cityName City name (e.g., "skopje", "bitola")
     * @return Base URL (e.g., "https://skopje.pulse.eco/rest")
     */
    private String baseUrl(String cityName) {
        // Convert city name to lowercase and build URL
        // Examples:
        // - "skopje" -> "https://skopje.pulse.eco/rest"
        // - "bitola" -> "https://bitola.pulse.eco/rest"
        return "https://" + cityName.toLowerCase() + ".pulse.eco/rest";
    }

    /**
     * Fetches current sensor data from pulse.eco API
     * 
     * Makes a GET request to: https://{cityName}.pulse.eco/rest/current
     * The API returns a JSON array of sensor readings.
     * 
     * @param cityName City name (e.g., "skopje", "bitola", "ohrid")
     * @return List of RawDataView objects (one per sensor reading)
     *         Returns empty list if API call fails or returns null
     */
    @Override
    public List<RawDataView> getCurrentData(String cityName) {
        // Build full URL: base URL + "/current"
        // Example: "https://skopje.pulse.eco/rest/current"
        String url = baseUrl(cityName) + "/current";
        
        // Make HTTP GET request and automatically deserialize JSON response to RawDataView array
        // RestTemplate handles authentication automatically (via interceptor in RestTemplateConfig)
        RawDataView[] response = restTemplate.getForObject(url, RawDataView[].class);
        
        // Return list (empty if response is null)
        return response == null ? List.of() : Arrays.asList(response);
    }
}