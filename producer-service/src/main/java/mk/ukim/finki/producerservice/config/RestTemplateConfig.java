package mk.ukim.finki.producerservice.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate
 * 
 * RestTemplate is used to make HTTP requests to external APIs (pulse.eco).
 * This configuration sets up Basic Authentication for all requests.
 * 
 * How it works:
 * 1. Takes username and password from PulseEcoProperties
 * 2. Encodes them in Base64 format (required for Basic Auth)
 * 3. Adds an interceptor that automatically adds the Authorization header to every request
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a RestTemplate bean with Basic Authentication configured
     * 
     * @param props Configuration properties containing pulse.eco credentials
     * @return RestTemplate instance with authentication interceptor
     */
    @Bean
    public RestTemplate restTemplate(PulseEcoProperties props) {
        RestTemplate restTemplate = new RestTemplate();

        // Combine username and password in format "username:password"
        String creds = props.getUsername() + ":" + props.getPassword();
        
        // Encode credentials in Base64 (required for HTTP Basic Authentication)
        // Example: "bobi:DishiDlaboko" -> "Ym9iaTpEaXNoaURsYWJva28="
        String base64Creds = Base64.getEncoder()
                .encodeToString(creds.getBytes(StandardCharsets.UTF_8));

        // Add an interceptor that automatically adds Authorization header to every HTTP request
        // This way, we don't need to manually add auth headers in each API call
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // Add "Authorization: Basic <base64-credentials>" header
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Basic " + base64Creds);
            // Continue with the request execution
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}