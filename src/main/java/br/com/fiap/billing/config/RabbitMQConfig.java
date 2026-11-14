package br.com.fiap.billing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "oficina.events";
    public static final String BILLING_QUEUE = "billing.os.queue";

    public static final String ROUTING_OS_ABERTA          = "os.aberta";
    public static final String ROUTING_ORCAMENTO_SOLICITADO = "orcamento.solicitado";

    @Bean
    public TopicExchange oficinaTopic() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue billingQueue() {
        return QueueBuilder.durable(BILLING_QUEUE).build();
    }

    @Bean
    public Binding bindingOsAberta(Queue billingQueue, TopicExchange oficinaTopic) {
        return BindingBuilder.bind(billingQueue).to(oficinaTopic).with(ROUTING_OS_ABERTA);
    }

    @Bean
    public Binding bindingOrcamentoSolicitado(Queue billingQueue, TopicExchange oficinaTopic) {
        return BindingBuilder.bind(billingQueue).to(oficinaTopic).with(ROUTING_ORCAMENTO_SOLICITADO);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
