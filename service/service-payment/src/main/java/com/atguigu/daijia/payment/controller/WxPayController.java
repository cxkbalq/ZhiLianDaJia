package com.atguigu.daijia.payment.controller;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.payment.service.WxPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Tag(name = "微信支付接口")
@RestController
@RequestMapping("/payment/wxPay")
@Slf4j
public class WxPayController {
    @Autowired
    private WxPayService wxPayService;
    //因为个人无法申请微信支付开发，这里只做相关逻辑处理，不对支持内容进行如何操作，以保证接口可以正常使用
    //因为个人无法申请微信支付开发，这里只做相关逻辑处理，不对支持内容进行如何操作，以保证接口可以正常使用

    @Operation(summary = "创建微信支付")
    @PostMapping("/createWxPayment")
    public Result<Boolean> createWxPayment(@RequestBody PaymentInfoForm paymentInfoForm) {
        return Result.ok(wxPayService.createWxPayment(paymentInfoForm));
    }

    @Operation(summary = "支付状态查询")
    @GetMapping("/queryPayStatus/{orderNo}")
    public Result queryPayStatus(@PathVariable String orderNo) {
        return Result.ok(wxPayService.queryPayStatus(orderNo));
    }

    @Operation(summary = "插入支付数据")
    @PostMapping("/insterPayInfo")
    public Result<Boolean> insterPayInfo(@RequestBody PaymentInfoForm paymentInfoForm) {
        return Result.ok(wxPayService.insterPayInfo(paymentInfoForm));
    }

}

