package com.atguigu.daijia.order.listener;

import com.atguigu.daijia.order.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringRabbitListener {
    @Autowired
    private OrderInfoService orderInfoService;

    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = "daijia.delay.direct",type = ExchangeTypes.DIRECT,delayed = "true"),
            value = @Queue(value = "daijia.delay.queue",durable = "true"),
            key = "qx"
    ))
    public void DelayMessage(Long orderId){
        log.info("Rabbit接收成功:"+"DelayMessage调用");
        orderInfoService.customerCancelNoAcceptOrder(orderId);
    }
}
