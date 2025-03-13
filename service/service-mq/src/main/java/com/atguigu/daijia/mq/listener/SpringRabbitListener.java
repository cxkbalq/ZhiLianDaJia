package com.atguigu.daijia.mq.listener;

import com.atguigu.daijia.map.client.WxPayFeignClient;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
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
    private OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    private WxPayFeignClient wxPayFeignClient;

    /**
     * 监听支付成功
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "PayCg", durable = "true"),
            exchange = @Exchange(name = "daijia.pay.exchange", type = ExchangeTypes.DIRECT),
            key = {"PayCg"}
    ))
   // @GlobalTransactional
    public void PayCg(PaymentInfoForm paymentInfoForm) {
        //更新订单支付状态
        orderInfoFeignClient.updateOrderPay(paymentInfoForm.getOrderNo());
        //插入支付表
        wxPayFeignClient.insterPayInfo(paymentInfoForm);

        log.info("支付成功，信息以更新完毕");
    }
}
