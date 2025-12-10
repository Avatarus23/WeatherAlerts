package mk.ukim.finki.gatewayservice.config;


import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway service listens on queue gw.alerts,
 * bound to alerts.topic with routing key alert.*.*
 */
@Configuration
public class RabbitConfig {

    public static final String ALERTS_EXCHANGE = "alerts.topic";
    public static final String GW_ALERTS_QUEUE = "gw.alerts";

    @Bean
    public TopicExchange alertsExchange() {
        return new TopicExchange(ALERTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue gatewayAlertsQueue() {
        return new Queue(GW_ALERTS_QUEUE, true);
    }

    @Bean
    public Binding gatewayAlertsBinding(Queue gatewayAlertsQueue,
                                        TopicExchange alertsExchange) {
        return BindingBuilder.bind(gatewayAlertsQueue)
                .to(alertsExchange)
                .with("alert.*.*");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}