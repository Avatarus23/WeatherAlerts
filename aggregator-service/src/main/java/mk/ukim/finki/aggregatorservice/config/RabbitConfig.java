package mk.ukim.finki.aggregatorservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ config for aggregator.
 *
 * Input:
 *  - Exchange: readings.topic
 *  - Queue:    agg.readings
 *  - Binding:  reading.#
 *
 * Output:
 *  - Exchange: alerts.topic
 */
@Configuration
public class RabbitConfig {

    public static final String READINGS_EXCHANGE = "readings.topic";
    public static final String ALERTS_EXCHANGE   = "alerts.topic";
    public static final String AGG_QUEUE         = "agg.readings";

    @Bean
    public TopicExchange readingsExchange() {
        return new TopicExchange(READINGS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue aggQueue() {
        return new Queue(AGG_QUEUE, true);
    }

    @Bean
    public Binding aggBinding(Queue aggQueue, TopicExchange readingsExchange) {
        // Receive all readings for all areas/metrics
        return BindingBuilder.bind(aggQueue)
                .to(readingsExchange)
                .with("reading.#");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
