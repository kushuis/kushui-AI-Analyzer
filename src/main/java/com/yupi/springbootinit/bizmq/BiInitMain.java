package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
public class BiInitMain {

    public static void main(String[] args) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            String EXCHANGE_NAME =  BiMqConstant.BI_EXCHANGE_NAME;
            String deadExchangeName = BiMqConstant.DEAD_EXCHANGE_NAME;
            //交换机声明
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");
            channel.exchangeDeclare(deadExchangeName, "direct");

            // 创建死信队列，随机分配一个队列名称
            //创建了死信队列并绑定后，当有死信就会进入死信队列
            channel.queueDeclare(BiMqConstant.DEAD_QUEUE_NAME, false, false, false, null);
            channel.queueBind(BiMqConstant.DEAD_QUEUE_NAME, deadExchangeName, BiMqConstant.DEAD_ROUTING_KEY);

            // 创建工作队列
            String queueName = BiMqConstant.BI_QUEUE_NAME;
            // 指定死信队列参数
            Map<String, Object> arg = new HashMap<>();
            // 要绑定到哪个交换机
            arg.put("x-dead-letter-exchange", BiMqConstant.DEAD_EXCHANGE_NAME);
            // 指定死信要转发到哪个死信队列
            arg.put("x-dead-letter-routing-key", BiMqConstant.DEAD_ROUTING_KEY);

            channel.queueDeclare(queueName, true, false, false, arg);
            channel.queueBind(queueName, EXCHANGE_NAME,  BiMqConstant.BI_ROUTING_KEY);
        } catch (Exception e) {

        }

    }
}
