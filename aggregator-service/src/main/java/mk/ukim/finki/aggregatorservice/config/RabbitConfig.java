package mk.ukim.finki.aggregatorservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ config for aggregator.
 * Declares:
 *  - readings.topic exchange (for input)
 *  - alerts.topic exchange (for output)
 *  - agg.Skopje queue bound to reading.Skopje.*
 */
@Configuration
public class RabbitConfig {

    public static final String READINGS_EXCHANGE = "readings.topic";
    public static final String ALERTS_EXCHANGE   = "alerts.topic";
    public static final String AGG_QUEUE         = "agg.Skopje";

    @Bean
    public TopicExchange readingsExchange() {
        return new TopicExchange(READINGS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue aggSkopjeQueue() {
        return new Queue(AGG_QUEUE, true);
    }

    @Bean
    public Binding aggSkopjeBinding(Queue aggSkopjeQueue, TopicExchange readingsExchange) {
        // Receive all readings for city Skopje (any signal)
        return BindingBuilder.bind(aggSkopjeQueue)
                .to(readingsExchange)
                .with("reading.Skopje.*");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}