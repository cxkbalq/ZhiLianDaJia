package com.atguigu.daijia.customer.service.impl;

import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.coupon.client.CouponFeignClient;
import com.atguigu.daijia.customer.client.CustomerInfoFeignClient;
import com.atguigu.daijia.customer.service.OrderService;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.client.DriverInfoFeignClient;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.map.client.WxPayFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.coupon.UseCouponForm;
import com.atguigu.daijia.model.form.customer.ExpectOrderForm;
import com.atguigu.daijia.model.form.customer.SubmitOrderForm;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.payment.CreateWxPaymentForm;
import com.atguigu.daijia.model.form.payment.PaymentInfoForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.customer.ExpectOrderVo;
import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.driver.DriverInfoVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderBillVo;
import com.atguigu.daijia.model.vo.order.OrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderPayVo;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {
    @Autowired
    private MapFeignClient mapFeignClient;

    @Autowired
    private FeeRuleFeignClient feeRuleFeignClient;

    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;

    @Autowired
    private NewOrderFeignClient newOrderFeignClient;

    @Autowired
    private DriverInfoFeignClient driverInfoFeignClient;

    @Autowired
    private LocationFeignClient locationFeignClient;

    @Autowired
    private WxPayFeignClient payFeignClient;
    @Autowired
    private ThreadPoolExecutor pool;
    @Autowired
    private CustomerInfoFeignClient customerInfoFeignClient;
    @Autowired
    private CouponFeignClient couponFeignClient;

    @Override
    public ExpectOrderVo expectOrder(ExpectOrderForm expectOrderForm) {
        //计算驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(expectOrderForm, calculateDrivingLineForm);
        DrivingLineVo drivingLineVo = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();

        //计算订单费用
        FeeRuleRequestForm calculateOrderFeeForm = new FeeRuleRequestForm();
        calculateOrderFeeForm.setDistance(drivingLineVo.getDistance());
        calculateOrderFeeForm.setStartTime(new Date());
        calculateOrderFeeForm.setWaitMinute(0);
        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(calculateOrderFeeForm).getData();

        //预估订单实体
        ExpectOrderVo expectOrderVo = new ExpectOrderVo();
        expectOrderVo.setDrivingLineVo(drivingLineVo);
        expectOrderVo.setFeeRuleResponseVo(feeRuleResponseVo);
        return expectOrderVo;
    }

    /**
     * 提交订单
     *
     * @param submitOrderForm
     * @return
     */
    @Override
    public Long submitOrder(SubmitOrderForm submitOrderForm) {
        //计算驾驶线路
        CalculateDrivingLineForm calculateDrivingLineForm = new CalculateDrivingLineForm();
        BeanUtils.copyProperties(submitOrderForm, calculateDrivingLineForm);
        DrivingLineVo drivingLineVo = mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();

        //计算订单费用
        FeeRuleRequestForm calculateOrderFeeForm = new FeeRuleRequestForm();
        calculateOrderFeeForm.setDistance(drivingLineVo.getDistance());
        calculateOrderFeeForm.setStartTime(new Date());
        calculateOrderFeeForm.setWaitMinute(0);
        FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(calculateOrderFeeForm).getData();

        //3.封装订单信息对象
        OrderInfoForm orderInfoForm = new OrderInfoForm();
        //订单位置信息
        BeanUtils.copyProperties(submitOrderForm, orderInfoForm);
        //预估里程
        orderInfoForm.setExpectDistance(drivingLineVo.getDistance());
        orderInfoForm.setExpectAmount(feeRuleResponseVo.getTotalAmount());

        //4.保存订单信息
        Long orderId = orderInfoFeignClient.saveOrderInfo(orderInfoForm).getData();

        //TODO启动任务调度
        NewOrderTaskVo newOrderDispatchVo = new NewOrderTaskVo();
        newOrderDispatchVo.setOrderId(orderId);
        newOrderDispatchVo.setStartLocation(orderInfoForm.getStartLocation());
        newOrderDispatchVo.setStartPointLongitude(orderInfoForm.getStartPointLongitude());
        newOrderDispatchVo.setStartPointLatitude(orderInfoForm.getStartPointLatitude());
        newOrderDispatchVo.setEndLocation(orderInfoForm.getEndLocation());
        newOrderDispatchVo.setEndPointLongitude(orderInfoForm.getEndPointLongitude());
        newOrderDispatchVo.setEndPointLatitude(orderInfoForm.getEndPointLatitude());
        newOrderDispatchVo.setExpectAmount(orderInfoForm.getExpectAmount());
        newOrderDispatchVo.setExpectDistance(orderInfoForm.getExpectDistance());
        newOrderDispatchVo.setExpectTime(drivingLineVo.getDuration());
        newOrderDispatchVo.setFavourFee(orderInfoForm.getFavourFee());
        newOrderDispatchVo.setCreateTime(new Date());
        //5.2.添加并执行任务调度
        Long jobId = newOrderFeignClient.addAndStartTask(newOrderDispatchVo).getData();
        log.info("订单id为： {}，绑定任务id为：{}", orderId, jobId);

        return orderId;

    }

    /**
     * 获取订单状态
     *
     * @param orderId
     * @return
     */
    @Override
    public Integer getOrderStatus(Long orderId) {
        return orderInfoFeignClient.getOrderStatus(orderId).getData();
    }

    /**
     * 查询订单
     *
     * @param customerId
     * @return
     */
    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        return orderInfoFeignClient.searchCustomerCurrentOrder(customerId).getData();
    }

    /**
     * 查询订单相关信息
     *
     * @param orderId
     * @param customerId
     * @return
     */
    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long customerId) {
        // 异步获取订单信息并校验客户ID
        CompletableFuture<OrderInfo> orderInfoFuture = CompletableFuture.supplyAsync(() ->
                        orderInfoFeignClient.getOrderInfo(orderId).getData(), pool)
                .thenApply(orderInfo -> {
                    if (orderInfo.getCustomerId().longValue() != customerId.longValue()) {
                        throw new zdyException(ResultCodeEnum.ILLEGAL_REQUEST);
                    }
                    return orderInfo;
                });

        // 并行获取司机信息和账单信息
        CompletableFuture<DriverInfoVo> driverInfoFuture = orderInfoFuture.thenComposeAsync(orderInfo ->
                        (orderInfo.getDriverId() != null) ?
                                CompletableFuture.supplyAsync(() ->
                                        driverInfoFeignClient.getDriverInfo(orderInfo.getDriverId()).getData(), pool) :
                                CompletableFuture.completedFuture(null),
                pool);

        CompletableFuture<OrderBillVo> orderBillFuture = orderInfoFuture.thenComposeAsync(orderInfo ->
                        (orderInfo.getStatus().intValue() >= OrderStatus.UNPAID.getStatus().intValue()) ?
                                CompletableFuture.supplyAsync(() ->
                                        orderInfoFeignClient.getOrderBillInfo(orderId).getData(), pool) :
                                CompletableFuture.completedFuture(null),
                pool);

        // 组合所有结果
        return orderInfoFuture
                .thenCombine(driverInfoFuture, (orderInfo, driverInfo) -> {
                    OrderInfoVo vo = new OrderInfoVo();
                    vo.setOrderId(orderId);
                    BeanUtils.copyProperties(orderInfo, vo);
                    vo.setDriverInfoVo(driverInfo);
                    return vo;
                })
                .thenCombine(orderBillFuture, (vo, orderBill) -> {
                    vo.setOrderBillVo(orderBill);
                    return vo;
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause();
                    if (cause instanceof zdyException) {
                        throw (zdyException) cause;
                    }
                    throw new RuntimeException("Failed to get order info", cause);
                })
                .join();
    }

    /**
     * 获取司机基本信息
     *
     * @param orderId
     * @param customerId
     * @return
     */
    @Override
    public DriverInfoVo getDriverInfo(Long orderId, Long customerId) {
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if (orderInfo.getCustomerId().longValue() != customerId.longValue()) {
            throw new zdyException(ResultCodeEnum.ILLEGAL_REQUEST);
        }
        return driverInfoFeignClient.getDriverInfo(orderInfo.getDriverId()).getData();
    }

    /**
     * 获取当前订单司机的实时位置
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderLocationVo getCacheOrderLocation(Long orderId) {
        return locationFeignClient.getCacheOrderLocation(orderId).getData();
    }

    /**
     * 功能作用未知，计算最佳驾驶线路
     *
     * @param calculateDrivingLineForm
     * @return
     */
    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }

    /**
     * 获取订单服务最后一个位置信息
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderServiceLastLocationVo getOrderServiceLastLocation(Long orderId) {
        return locationFeignClient.getOrderServiceLastLocation(orderId).getData();
    }

    /**
     * 获取乘客订单分页列表
     *
     * @param customerId
     * @param page
     * @param limit
     * @return
     */
    @Override
    public PageVo findCustomerOrderPage(Long customerId, Long page, Long limit) {
        return orderInfoFeignClient.findCustomerOrderPage(customerId, page, limit).getData();
    }

    /**
     * 乘客取消订单
     *
     * @param orderId
     * @return
     */
    @Override
    public Boolean customerCancelNoAcceptOrder(Long orderId) {
        return newOrderFeignClient.customerCancelNoAcceptOrder(orderId).getData();
    }

    /**
     * 创建微信支付
     *
     * @param createWxPaymentForm
     * @return
     */
    @Override
    @GlobalTransactional
    public Boolean createWxPayment(CreateWxPaymentForm createWxPaymentForm) {
        PaymentInfoForm paymentInfoForm = new PaymentInfoForm();
        paymentInfoForm.setOrderNo(createWxPaymentForm.getOrderNo());
        paymentInfoForm.setPayWay(1);
        OrderPayVo data = orderInfoFeignClient.getOrderPayVo(createWxPaymentForm.getOrderNo(), createWxPaymentForm.getCustomerId()).getData();
        //获取司机微信openId
        paymentInfoForm.setDriverOpenId(String.valueOf(driverInfoFeignClient.getDriverOpenId(data.getDriverId()).getData()));
        //乘客微信openId
        paymentInfoForm.setCustomerOpenId(String.valueOf(customerInfoFeignClient.getCustomerOpenId(data.getCustomerId()).getData()));

        //处理优惠券
        BigDecimal couponAmount = null;
        //支付时选择过一次优惠券，如果支付失败或未支付，下次支付时不能再次选择，只能使用第一次选中的优惠券（前端已控制，后端再次校验）
        if (null == data.getCouponAmount() && null != createWxPaymentForm.getCustomerCouponId() && createWxPaymentForm.getCustomerCouponId() != 0) {
            UseCouponForm useCouponForm = new UseCouponForm();
            useCouponForm.setOrderId(data.getOrderId());
            useCouponForm.setCustomerCouponId(createWxPaymentForm.getCustomerCouponId());
            useCouponForm.setOrderAmount(data.getPayAmount());
            useCouponForm.setCustomerId(createWxPaymentForm.getCustomerId());
            couponAmount = couponFeignClient.useCoupon(useCouponForm).getData();
        }

        //更新账单优惠券金额
        //支付金额
        BigDecimal payAmount = data.getPayAmount();
        if (null != couponAmount) {
            Boolean isUpdate = orderInfoFeignClient.updateCouponAmount(data.getOrderId(), couponAmount).getData();
            if (!isUpdate) {
                throw new zdyException(ResultCodeEnum.DATA_ERROR);
            }
            //当前支付金额 = 支付金额 - 优惠券金额
            payAmount = payAmount.subtract(couponAmount);
        }

        paymentInfoForm.setAmount(payAmount);
        return payFeignClient.createWxPayment(paymentInfoForm).getData();
    }

    /**
     * 查询订单状态
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean queryPayStatus(String orderNo) {
        return payFeignClient.queryPayStatus(orderNo).getData();
    }
}
