package mk.ukim.finki.producerservice.pulseeco;

import java.util.List;

// Interface for Pulse.eco API Client
public interface PulseEcoClient {

    /**
     * Fetches current sensor data for a specific city
     * 
     * Makes an API call to: https://{cityName}.pulse.eco/rest/current
     * 
     * @param cityName City name (e.g., "skopje", "bitola", "ohrid")
     * @return List of RawDataView objects containing sensor readings
     *         (one object per sensor reading - e.g., PM10, temperature, etc.)
     */

    List<RawDataView> getCurrentData(String cityName);

    // You can add more methods later:
    // List<RawDataView> getDataRaw(...);  // For historical data
    // List<SensorView> getSensors(...);   // For sensor metadata
}