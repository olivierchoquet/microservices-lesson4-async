package com.ipl.order.messaging;

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
 * Declare la topologie au demarrage d'order-service. Spring cree
 * l'exchange, les queues et les bindings s'ils n'existent pas encore.
 *
 * Rappel des roles :
 *   exchange    = le bureau de tri, il ne stocke rien
 *   routing key = ce que l'expediteur ecrit sur l'enveloppe
 *   binding     = la regle de tri affichee sur une boite aux lettres
 *   queue       = la boite aux lettres, elle garde les messages
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "orders.exchange";

    // Publication
    public static final String RK_ORDER_CREATED = "order.created";

    /*
     * Consommation : UNE QUEUE PAR TYPE D'EVENEMENT.
     *
     * On aurait pu binder une seule queue sur "stock.*" pour capter les deux
     * reponses. Mais deux @RabbitListener sur la meme queue se partagent les
     * messages au lieu de les filtrer par type : la moitie des messages
     * arriverait sur la mauvaise methode. Une queue par type evite le piege.
     */
    public static final String QUEUE_STOCK_RESERVED = "stock.reserved.queue";
    public static final String QUEUE_STOCK_REJECTED = "stock.rejected.queue";

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    Queue stockReservedQueue() {
        return new Queue(QUEUE_STOCK_RESERVED, true);   // durable : survit au redemarrage du broker
    }

    @Bean
    Queue stockRejectedQueue() {
        return new Queue(QUEUE_STOCK_REJECTED, true);
    }

    @Bean
    Binding stockReservedBinding(Queue stockReservedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(stockReservedQueue).to(ordersExchange).with("stock.reserved");
    }

    @Bean
    Binding stockRejectedBinding(Queue stockRejectedQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(stockRejectedQueue).to(ordersExchange).with("stock.rejected");
    }

    /*
     * Converter JSON. Sans lui, Spring serialise en binaire Java : le payload
     * est illisible dans la console RabbitMQ.
     *
     * TypePrecedence.INFERRED est INDISPENSABLE ici. Par defaut, l'expediteur
     * ecrit le nom complet de sa classe dans l'en-tete __TypeId__, et le
     * destinataire essaie de charger cette classe. Comme nos deux services
     * ont des packages differents (com.ipl.order vs com.ipl.product), le
     * chargement echouerait. INFERRED dit a Spring d'ignorer l'en-tete et
     * d'utiliser le type du parametre de la methode @RabbitListener.
     */
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
