package mk.ukim.finki.gatewayservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway service RabbitMQ configuration.
 *
 * WHAT THIS DOES:
 * 1. Declares alerts.topic exchange (receives alerts from aggregator)
 * 2. Creates queue "gw.alerts" bound with pattern "alert.*.*"
 * 3. Sets up Dead Letter Queue for failed WebSocket forwarding
 * 4. Configures listener factory for proper message handling
 *
 * RABBITMQ CONCEPTS:
 *
 * ROUTING KEY PATTERN "alert.*.*":
 * - Matches: alert.centar.RED, alert.gazi_baba.YELLOW, etc.
 * - First * = area, second * = level
 * - Receives all alerts regardless of area or level
 *
 * DEAD LETTER QUEUE:
 * - If WebSocket forwarding fails, message goes to DLQ
 * - Prevents alert loss if WebSocket connection is down
 *
 * Gateway service listens on queue gw.alerts,
 * bound to alerts.topic with routing key alert.*.*
 */
@Configuration
public class RabbitConfig {

    public static final String ALERTS_EXCHANGE = "alerts.topic";
    public static final String GW_ALERTS_QUEUE = "gw.alerts";
    
    // Dead Letter Exchange and Queue
    public static final String DLX = "dlx";
    public static final String DLQ = "dlq.gw.alerts";

    /**
     * Alerts exchange - receives alerts from aggregator
     */
    @Bean
    public TopicExchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    /**
     * Dead Letter Exchange
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    /**
     * Gateway alerts queue with Dead Letter Queue configuration.
     * If message processing fails, it goes to DLQ instead of being lost.
     */
    @Bean
    public Queue gatewayAlertsQueue() {
        return QueueBuilder.durable(GW_ALERTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .withArgument("x-message-ttl", 60000) // 60 seconds TTL
                .build();
    }

    /**
     * Dead Letter Queue for failed alerts
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    /**
     * Binds gateway queue to alerts exchange.
     * Pattern "alert.*.*" receives all alerts (any area, any level)
     */
    @Bean
    public Binding gatewayAlertsBinding(Queue gatewayAlertsQueue,
                                        TopicExchange alertsExchange) {
        return BindingBuilder.bind(gatewayAlertsQueue)
                .to(alertsExchange)
                .with("alert.*.*");
    }

    /**
     * Binds Dead Letter Queue to Dead Letter Exchange
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue,
                                     DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ);
    }

    /**
     * JSON message converter
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Listener container factory - configures message consumption
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        // Don't requeue rejected messages - send to DLQ
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}