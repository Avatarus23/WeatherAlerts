package mk.ukim.finki.gatewayservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration to ensure Actuator endpoints are accessible.
 * 
 * This ensures that WebSocket configuration doesn't interfere
 * with Actuator endpoints.
 */
@Configuration
public class ActuatorConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // Ensure actuator paths are matched correctly
        configurer.setUseTrailingSlashMatch(false);
    }
}

