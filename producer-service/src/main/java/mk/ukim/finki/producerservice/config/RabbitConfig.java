package mk.ukim.finki.producerservice.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for producer.
 * Declares the readings.topic exchange and a RabbitTemplate with JSON conversion.
 */
@Configuration
public class RabbitConfig {

    public static final String READINGS_EXCHANGE = "readings.topic";

    @Bean
    public TopicExchange readingsExchange() {
        // durable=true, autoDelete=false
        return new TopicExchange(READINGS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Use Jackson to convert Java objects <-> JSON
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
