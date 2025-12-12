package mk.ukim.finki.producerservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ Configuration
 * 
 * This class configures RabbitMQ messaging infrastructure:
 * - Exchange: Where messages are published (like a post office)
 * - Queue: Where messages are stored for consumers (like a mailbox)
 * - Binding: Connects exchange to queue with routing rules
 * - Message Converter: Converts Java objects to JSON for RabbitMQ
 * 
 * RabbitMQ Architecture:
 * Producer -> Exchange -> (routing key) -> Queue -> Consumer
 * 
 * Example routing keys we use:
 * - "measurement.skopje.pm10" (PM10 measurements from Skopje)
 * - "measurement.bitola.temperature" (Temperature from Bitola)
 */
@Configuration
public class RabbitMQConfig {

    /** Name of the topic exchange (allows routing based on patterns) */
    public static final String EXCHANGE_NAME = "weather.exchange";
    
    /** Name of the queue where measurements will be stored */
    public static final String QUEUE_NAME = "weather.measurements.queue";
    
    /** Routing key pattern: "measurement.#" matches all keys starting with "measurement." */
    public static final String ROUTING_KEY = "measurement.#";

    /**
     * Creates a Topic Exchange
     * 
     * Topic exchanges route messages based on routing key patterns.
     * This allows flexible routing (e.g., "measurement.skopje.*" or "measurement.*.pm10")
     * 
     * @return TopicExchange bean
     */
    @Bean
    public TopicExchange weatherExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    /**
     * Creates a durable queue
     * 
     * Durable means the queue survives RabbitMQ server restarts.
     * Messages in this queue will be consumed by other services (consumers).
     * 
     * @return Queue bean
     */
    @Bean
    public Queue weatherQueue() {
        return QueueBuilder.durable(QUEUE_NAME).build();
    }

    /**
     * Binds the queue to the exchange with a routing key pattern
     * 
     * This binding tells RabbitMQ:
     * "Send all messages from weather.exchange with routing key matching 'measurement.#' 
     *  to the weather.measurements.queue"
     * 
     * The '#' wildcard matches zero or more words.
     * 
     * @return Binding bean
     */
    @Bean
    public Binding weatherBinding() {
        return BindingBuilder
                .bind(weatherQueue())      // Bind this queue...
                .to(weatherExchange())     // ...to this exchange...
                .with(ROUTING_KEY);        // ...with this routing pattern
    }

    /**
     * Configures JSON message converter
     * 
     * This converter automatically converts Java objects (like CityMeasurement)
     * to JSON when sending to RabbitMQ, and JSON back to Java objects when receiving.
     * 
     * Note: Jackson2JsonMessageConverter is deprecated in Spring Boot 4.0
     * but still functional. Consider migrating to newer converter when available.
     * 
     * @return MessageConverter bean
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Creates RabbitTemplate for sending messages
     * 
     * RabbitTemplate is the main class for interacting with RabbitMQ.
     * It provides methods like convertAndSend() to publish messages.
     * 
     * @param connectionFactory Automatically provided by Spring Boot
     * @return RabbitTemplate bean configured with JSON converter
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // Set the converter so messages are automatically serialized to JSON
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}

