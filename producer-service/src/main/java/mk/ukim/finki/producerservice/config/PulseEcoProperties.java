package mk.ukim.finki.producerservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseeco")
public class PulseEcoProperties {

    // pulse.eco auth to API
    private String username;
    private String password;
    
    // list of citieas
    private List<String> cities;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public List<String> getCities() { return cities; }
    public void setCities(List<String> cities) { this.cities = cities; }
}