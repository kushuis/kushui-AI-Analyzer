package com.kushui.springbootinit.config;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Autowired
    private CachingConnectionFactory connectionFactory;

    @Bean
    public SimpleRabbitListenerContainerFactory mqConsumerlistenerContainer(){
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        //假定 RabbitMQ 队列有 N 个消费队列，RabbitMQ 队列中的消息将以轮询的方式发送给消费者。
        //消息的数量是 M,那么每个消费者得到的数据就是 M%N。
        //设置每个消费者的prefetch count(预取数量)防止消费者压力大
        factory.setPrefetchCount(50);
        return factory;
    }

}
