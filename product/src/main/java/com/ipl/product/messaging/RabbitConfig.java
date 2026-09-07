package com.ipl.product.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * +++ FICHIER ENTIEREMENT NOUVEAU
 *
 * Cote product-service la topologie est le miroir de celle d'order-service :
 *   - il CONSOMME order.created         -> une queue et un binding
 *   - il PUBLIE stock.reserved/rejected -> le meme exchange
 *
 * Declarer l'exchange des deux cotes n'est pas un doublon : la declaration
 * est idempotente. Le premier service demarre le cree, le second constate
 * qu'il existe. Cela evite qu'un service refuse de demarrer parce que
 * l'autre n'a pas encore tourne.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "orders.exchange";

    // Consommation
    public static final String QUEUE_ORDER_CREATED = "order.created.queue";
    public static final String BK_ORDER_CREATED = "order.created";

    // Publication
    public static final String RK_STOCK_RESERVED = "stock.reserved";
    public static final String RK_STOCK_REJECTED = "stock.rejected";

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue orderCreatedQueue() {
        return new Queue(QUEUE_ORDER_CREATED, true);
    }

    /*
     * Binding EXACT, et non "order.*".
     * Cette queue declenche une reservation de stock : on ne veut surtout
     * pas qu'un futur order.cancelled y arrive et soit traite comme une
     * demande de reservation.
     */
    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(ordersExchange).with(BK_ORDER_CREATED);
    }

    // Voir le commentaire detaille dans order/messaging/RabbitConfig.java
    @Bean
    MessageConverter jsonConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
