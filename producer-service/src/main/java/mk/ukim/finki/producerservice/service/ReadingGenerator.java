package mk.ukim.finki.producerservice.service;

import mk.ukim.finki.producerservice.config.RabbitConfig;
import mk.ukim.finki.producerservice.model.ReadingMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;

/**
 * Test generator that simulates pm25 readings for Skopje
 * and publishes them periodically to RabbitMQ.
 */
@Service
public class ReadingGenerator {

    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();

    public ReadingGenerator(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Every 5 seconds, generate a random pm25 reading and send it to RabbitMQ.
     */
    @Scheduled(fixedRate = 5000)
    public void generateAndSend() {
        double value = 10 + random.nextDouble() * 100; // 10-110 random

        ReadingMessage msg = new ReadingMessage(
                "Skopje",
                "pm25",
                value,
                "µg/m³",
                Instant.now()
        );

        // routing key: reading.Skopje.pm25
        String routingKey = "reading." + msg.getCity() + "." + msg.getSignal();

        rabbitTemplate.convertAndSend(
                RabbitConfig.READINGS_EXCHANGE,
                routingKey,
                msg
        );

        System.out.println("[PRODUCER] Sent reading: " + value);
    }
}



/*@Service
public class ReadingPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final PulseEcoClient pulseEcoClient;

    public ReadingPublisher(RabbitTemplate rabbitTemplate,
                            PulseEcoClient pulseEcoClient) {
        this.rabbitTemplate = rabbitTemplate;
        this.pulseEcoClient = pulseEcoClient;
    }

    @Scheduled(fixedRate = 60_000)
    public void fetchAndPublish() {
        pulseEcoClient.fetchLatestSkopje().forEach(this::publishOne);
    }

    private void publishOne(ReadingMessage reading) {
        String routingKey = "reading." + reading.getCity() + "." + reading.getSignal();
        rabbitTemplate.convertAndSend(RabbitConfig.READINGS_EXCHANGE, routingKey, reading);
    }
}*/