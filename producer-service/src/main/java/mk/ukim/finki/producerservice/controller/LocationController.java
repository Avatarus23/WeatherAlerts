package mk.ukim.finki.producerservice.controller;

import mk.ukim.finki.producerservice.model.CityMeasurement;
import mk.ukim.finki.producerservice.pulseeco.PulseEcoClient;
import mk.ukim.finki.producerservice.pulseeco.RawDataView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Location-Based Queries
 * 
 * This controller provides REST API endpoints to query measurements
 * based on location/position. It allows you to:
 * - Get measurements for a specific position/location
 * - Filter by city
 * - Get all available positions in a city
 * 
 * All endpoints are prefixed with "/api/location"
 */
@RestController
@RequestMapping("/api/location")
public class LocationController {

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);

    /** Client for fetching data from pulse.eco APIs */
    private final PulseEcoClient pulseEcoClient;

    /**
     * Constructor - Spring automatically injects PulseEcoClient
     */
    public LocationController(PulseEcoClient pulseEcoClient) {
        this.pulseEcoClient = pulseEcoClient;
    }

    /**
     * Get measurements for a specific location/position
     * 
     * Endpoint: GET /api/location/{position}?city={cityName}
     * 
     * Examples:
     * - GET /api/location/41.9981,21.4254
     *   (searches all configured cities for this position)
     * - GET /api/location/41.9981,21.4254?city=skopje
     *   (searches only in Skopje)
     * 
     * @param position The position/location identifier (e.g., coordinates like "41.9981,21.4254")
     * @param city Optional city filter (if provided, only searches in that city)
     * @return List of measurements matching the position
     */
    @GetMapping("/{position}")
    public ResponseEntity<List<CityMeasurement>> getMeasurementsByPosition(
            @PathVariable String position,
            @RequestParam(required = false) String city) {
        try {
            List<CityMeasurement> measurements;

            if (city != null && !city.isEmpty()) {
                // If city parameter is provided, search only in that city
                measurements = getMeasurementsForCityAndPosition(city, position);
            } else {
                // If no city specified, search across all default cities
                measurements = getMeasurementsForPosition(position);
            }

            return ResponseEntity.ok(measurements);
        } catch (Exception e) {
            log.error("Error fetching measurements for position {}: {}", position, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get measurements for a specific city and position
     * 
     * Endpoint: GET /api/location/city/{cityName}/position/{position}
     * 
     * Example:
     * - GET /api/location/city/skopje/position/41.9981,21.4254
     * 
     * @param cityName City name (e.g., "skopje", "bitola")
     * @param position Position identifier (e.g., coordinates)
     * @return List of measurements for that city and position
     */
    @GetMapping("/city/{cityName}/position/{position}")
    public ResponseEntity<List<CityMeasurement>> getMeasurementsByCityAndPosition(
            @PathVariable String cityName,
            @PathVariable String position) {
        try {
            List<CityMeasurement> measurements = getMeasurementsForCityAndPosition(cityName, position);
            return ResponseEntity.ok(measurements);
        } catch (Exception e) {
            log.error("Error fetching measurements for city {} position {}: {}", 
                    cityName, position, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all available positions/sensors for a specific city
     * 
     * Endpoint: GET /api/location/city/{cityName}/positions
     * 
     * This is useful to discover what positions are available before querying.
     * 
     * Example:
     * - GET /api/location/city/skopje/positions
     *   Returns: ["41.9981,21.4254", "42.0012,21.4300", ...]
     * 
     * @param cityName City name (e.g., "skopje", "bitola")
     * @return List of unique position identifiers (coordinates) in that city
     */
    @GetMapping("/city/{cityName}/positions")
    public ResponseEntity<List<String>> getPositionsForCity(@PathVariable String cityName) {
        try {
            // Fetch all current data for the city
            List<RawDataView> rawList = pulseEcoClient.getCurrentData(cityName);
            
            // Extract unique positions from the data
            List<String> positions = rawList.stream()
                    .map(RawDataView::getPosition)           // Get position field
                    .filter(pos -> pos != null && !pos.isEmpty())  // Filter out null/empty
                    .distinct()                              // Remove duplicates
                    .collect(Collectors.toList());          // Collect to list
            
            return ResponseEntity.ok(positions);
        } catch (Exception e) {
            log.error("Error fetching positions for city {}: {}", cityName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Helper method: Get measurements for a specific city and position
     * 
     * @param cityName City name
     * @param position Position identifier
     * @return List of measurements matching the criteria
     */
    private List<CityMeasurement> getMeasurementsForCityAndPosition(String cityName, String position) {
        // Fetch all data for the city
        List<RawDataView> rawList = pulseEcoClient.getCurrentData(cityName);
        
        // Filter by position (case-insensitive) and convert to CityMeasurement
        return rawList.stream()
                .filter(raw -> position.equalsIgnoreCase(raw.getPosition()))  // Match position
                .map(raw -> toMeasurement(cityName, raw))                     // Convert format
                .collect(Collectors.toList());                               // Collect results
    }

    /**
     * Helper method: Get measurements for a position across multiple cities
     * 
     * Searches in a predefined list of cities. If you want to make this configurable,
     * you could inject PulseEcoProperties and use properties.getCities() instead.
     * 
     * @param position Position identifier
     * @return List of measurements from all cities matching the position
     */
    private List<CityMeasurement> getMeasurementsForPosition(String position) {
        // Default cities to search - you might want to make this configurable
        String[] cities = {"skopje", "bitola", "ohrid", "veles"};
        
        // Search each city and combine results
        return java.util.Arrays.stream(cities)
                .flatMap(city -> {
                    try {
                        // Fetch data for this city, filter by position, convert format
                        return pulseEcoClient.getCurrentData(city).stream()
                                .filter(raw -> position.equalsIgnoreCase(raw.getPosition()))
                                .map(raw -> toMeasurement(city, raw));
                    } catch (Exception e) {
                        // If one city fails, log warning but continue with other cities
                        log.warn("Failed to fetch data for city {}: {}", city, e.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * Helper method: Convert RawDataView to CityMeasurement
     * 
     * Same conversion logic as in CityProducerScheduler.
     * Handles parsing errors gracefully.
     * 
     * @param city City name
     * @param raw Raw data from API
     * @return CityMeasurement object
     */
    private CityMeasurement toMeasurement(String city, RawDataView raw) {
        // Parse timestamp
        Instant ts;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(raw.getStamp(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            ts = odt.toInstant();
        } catch (Exception e) {
            // Fallback to current time if parsing fails
            ts = Instant.now();
        }

        // Parse numeric value
        double value;
        try {
            value = Double.parseDouble(raw.getValue());
        } catch (NumberFormatException ex) {
            // Use NaN if value cannot be parsed
            value = Double.NaN;
        }

        // Create and return CityMeasurement
        return new CityMeasurement(
                city.toUpperCase(),
                "unknown",
                raw.getSensorId(),
                raw.getPosition(),
                ts,
                raw.getType(),
                value
        );
    }
}

