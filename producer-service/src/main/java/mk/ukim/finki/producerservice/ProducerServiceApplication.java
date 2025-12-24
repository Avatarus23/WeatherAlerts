package mk.ukim.finki.producerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import mk.ukim.finki.producerservice.config.PulseEcoProperties;

/**
 * Main Spring Boot Application Class
 * Producer Service application which fetches environmental data from pulse.eco APIs
 * 
 * Responsibilities:
 * - Fetches environmental data (air quality, temperature, etc.) from pulse.eco APIs
 * - Publishes the data to RabbitMQ for other services to consume
 * - Provides REST API endpoints to query measurements by location
 * 
 * Key Features:
 * - @EnableScheduling: Enables scheduled tasks (like periodic data fetching)
 * - @EnableConfigurationProperties: Allows reading configuration from application.properties
 * - @EnableRetry: Enables automatic retry for @Retryable methods
 */
@SpringBootApplication
@EnableScheduling  // Allows @Scheduled methods to run (e.g., CityProducerScheduler)
@EnableConfigurationProperties(PulseEcoProperties.class)  // Loads pulseeco.* properties
@EnableRetry  // Enables @Retryable annotation for automatic retries
public class ProducerServiceApplication {

    /**
     * Application entry point
     * Starts the Spring Boot application context and all configured beans
     */
    public static void main(String[] args) {
        SpringApplication.run(ProducerServiceApplication.class, args);
    }

}