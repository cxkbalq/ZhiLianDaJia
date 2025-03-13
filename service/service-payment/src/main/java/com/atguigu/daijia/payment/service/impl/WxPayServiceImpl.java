package com.atguigu.daijia.payment.service.impl;

import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.model.entity.payment.PaymentInfo;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.payment.mapper.PaymentInfoMapper;
import com.atguigu.daijia.payment.service.WxPayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private PaymentInfoMapper paymentInfoMapper;
    @Autowired
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;


    /**
     * 查询支付状态
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean queryPayStatus(String orderNo) {

        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo, orderNo));
        if (paymentInfo != null) {
            return true;
        }
        return false;
    }

    /**
     * 创建支付
     *
     * @param paymentInfoForm
     * @return
     */
    @Override
    @Transactional
    public Boolean createWxPayment(PaymentInfoForm paymentInfoForm) {
        /* 在实际业务里。后续更新操作应该在回调里进行操作，因微信限制原因
         ，只能写在这里模拟支付，查询也应该向微信服务器进行查询，这里全都用于测试*/

        //先向数据查看是否插入数据了
        String exchange = "daijia.pay.exchange";
        AtomicReference<Boolean> flag = new AtomicReference<>(false);
        //确保投递成功，以及唯一性
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        //AMQP 2.1+：CorrelationData 的 getFuture() 返回的是 CompletableFuture，其回调方法为 whenComplete，而非旧版的 addCallback。
        correlationData.getFuture().whenComplete(((confirm, throwable) -> {
            if (throwable != null) {
                log.error("消息发送失败", throwable);
            } else {
                if (confirm.isAck()) {
                    log.info("消息投递成功, ID: {}", correlationData.getId());
                    flag.set(true);
                } else {
                    log.error("消息投递失败, ID: {}", correlationData.getId());
                }
            }
        }));
        rabbitTemplate.convertAndSend(exchange, "PayCg", paymentInfoForm,correlationData);
        return flag.get();
    }

    /**
     * 插入支付信息
     *
     * @param paymentInfoForm
     * @return
     */
    @Override
    public Boolean insterPayInfo(PaymentInfoForm paymentInfoForm) {
        PaymentInfo paymentInfo = new PaymentInfo();
        BeanUtils.copyProperties(paymentInfoForm, paymentInfo);
        paymentInfo.setCreateTime(new Date());
        paymentInfo.setUpdateTime(new Date());
        paymentInfo.setPayWay(1);
        //根据订单id查询用户id和司机id
//        paymentInfo.setCustomerOpenId(customerInfoFeignClient.getCustomerOpenId());
//        paymentInfo.setDriverOpenId();
        paymentInfoMapper.insert(paymentInfo);
        return true;
    }
}
