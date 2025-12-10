package mk.ukim.finki.weatherproducerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WeatherProducerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherProducerServiceApplication.class, args);
    }

}
