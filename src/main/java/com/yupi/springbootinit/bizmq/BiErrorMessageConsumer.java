package com.yupi.springbootinit.bizmq;

import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class BiErrorMessageConsumer {

    @Resource
    private ChartService chartService;

    //设置这个消费者原因是 可能出现消息过期，消息溢出导致消息成为死信，那么会由定时任务处理
    //这样做也由坏处，当任务执行失败时，定时器刚好从数据库监听到去处理这个任务，处理完后，由于这个信息进入死信队列
    //那么状态又会被修改为failed，定时任务又会去再执行一次，造成了浪费
    //解决方式是一定要在修改状态前判断一下状态是succeed还是别的状态
    @SneakyThrows //在不捕获异常的情况下让方法抛出异常
    //设置这个队列创建5-10个消费者，并指定消费工厂，config类中有工厂的配置
    @RabbitListener(queues = {BiMqConstant.DEAD_QUEUE_NAME}, ackMode = "MANUAL", concurrency = "1-5", containerFactory = "mqConsumerlistenerContainer")
//手动确认 还有"AUTO"
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        System.out.println("==============进入死信消费者=============");

        if (StringUtils.isBlank(message)) {
            // 如果失败，消息拒绝
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if(!chart.getStatus().equals("succeed")){
            chart.setStatus("failed");
            boolean updateResult = chartService.updateById(chart);
            if (!updateResult) {
                System.out.println("=================死信消费者中更新状态失败===================");
                channel.basicNack(deliveryTag, false, false);
            }
        }

        // 消息确认
        channel.basicAck(deliveryTag, false);

    }
}