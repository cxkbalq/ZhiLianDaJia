package com.atguigu.daijia.payment.service;

import com.atguigu.daijia.model.form.payment.PaymentInfoForm;

public interface WxPayService {


    Boolean queryPayStatus(String orderNo);

    Boolean createWxPayment(PaymentInfoForm paymentInfoForm);

    Boolean insterPayInfo(PaymentInfoForm paymentInfoForm);
}
