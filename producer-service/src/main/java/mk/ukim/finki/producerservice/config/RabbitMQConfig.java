package mk.ukim.finki.producerservice.config;

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
 * Producer publishes messages to the topic exchange:
 *   readings.topic
 *
 * Queues/bindings are declared by consuming services (aggregator, etc.).
 */
@Configuration
public class RabbitMQConfig {

    /** Topic exchange used for incoming readings (producer publishes here). */
    public static final String EXCHANGE_NAME = "readings.topic";

    @Bean
    public TopicExchange readingsExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
