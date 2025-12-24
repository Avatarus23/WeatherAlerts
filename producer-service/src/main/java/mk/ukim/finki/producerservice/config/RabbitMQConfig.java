package mk.ukim.finki.producerservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producer-side RabbitMQ configuration.
 *
 * WHAT THIS DOES:
 * 1. Declares the "readings.topic" exchange where producer publishes messages
 * 2. Configures JSON message converter for automatic serialization
 * 3. Sets up RabbitTemplate with publisher confirms and return callbacks
 *
 * RABBITMQ CONCEPTS:
 * - Exchange: Routes messages based on routing keys (topic exchange = pattern matching)
 * - Routing Key: Pattern like "reading.area.metric" that determines message routing
 * - Publisher Confirms: Ensures messages are received by the exchange
 * - Return Callbacks: Handles messages that couldn't be routed to any queue
 *
 * Producer publishes messages to the topic exchange:
 *   readings.topic
 *
 * Queues/bindings are declared by consuming services (aggregator, etc.).
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    /** Topic exchange used for incoming readings (producer publishes here). */
    public static final String EXCHANGE_NAME = "readings.topic";

    /**
     * Creates a durable topic exchange.
     * Durable = survives broker restarts
     * Topic exchange = routes messages based on routing key patterns
     */
    @Bean
    public TopicExchange readingsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    /**
     * JSON message converter - automatically converts Java objects to/from JSON
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Enhanced RabbitTemplate with publisher confirms and return callbacks.
     *
     * PUBLISHER CONFIRMS:
     * - When enabled, RabbitMQ sends a confirmation after receiving the message
     * - Helps ensure messages aren't lost during transmission
     *
     * RETURN CALLBACKS:
     * - Triggered when a message can't be routed to any queue
     * - Usually means no queue is bound to the exchange with matching routing key
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        
        // Enable mandatory flag - messages must be routed to at least one queue
        template.setMandatory(true);
        
        // Publisher confirm callback - called when RabbitMQ confirms message receipt
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("Message confirmed by RabbitMQ exchange");
            } else {
                log.error("Message NOT confirmed by RabbitMQ. Cause: {}", cause);
            }
        });
        
        // Return callback - called when message can't be routed to any queue
        template.setReturnsCallback((returned) -> {
            log.error("Message returned (not routed to any queue): routingKey={}, replyCode={}, replyText={}",
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText());
        });
        
        return template;
    }
}
