package com.kushui.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TtlConsumer {

    private final static String QUEUE_NAME = "ttl_queue";

    public static void main(String[] argv) throws Exception {
        // 创建连接
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        // 创建队列，指定消息过期参数
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("x-message-ttl", 5000);
        // args 指定参数  这里是给队列中所有消息设置过期时间,也可以指定死信交换机，
        //在队列声明的时候绑定死信交换机和路由
        // 指定死信队列参数
//        Map<String, Object> args = new HashMap<>();
//        // 要绑定到哪个交换机
//        args.put("x-dead-letter-exchange", DEAD_EXCHANGE_NAME);
//        // 指定死信要转发到哪个死信队列
//        args.put("x-dead-letter-routing-key", "waibao");
        channel.queueDeclare(QUEUE_NAME, false, false, false, args);
        //队列声明的四个参数
        //durable -消息队列重启后消息是否丢失
        //exclusive -独占队列，只允许一个连接访问，在关闭连接时自动删除
        //autoDelete -当没有consumer时是否删除队列

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        // 定义了如何处理消息
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            System.out.println(" [x] Received '" + message + "'");
        };
        // 消费消息，会持续阻塞,第四个参数是消费者取消订阅队列时，回调的函数
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });
    }
}