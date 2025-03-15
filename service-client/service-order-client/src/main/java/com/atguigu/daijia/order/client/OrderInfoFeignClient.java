package com.atguigu.daijia.order.client;

import com.atguigu.daijia.common.result.Result;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.CurrentOrderInfoVo;
import com.atguigu.daijia.model.vo.order.OrderBillVo;
import com.atguigu.daijia.model.vo.order.OrderPayVo;
import com.atguigu.daijia.model.vo.order.OrderProfitsharingVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;


@FeignClient(value = "service-order")
public interface OrderInfoFeignClient {
    /**
     * 更新优惠券金额
     *
     * @param orderId
     * @param couponAmount
     * @return
     */
    @GetMapping("/order/info/updateCouponAmount/{orderId}/{couponAmount}")
    Result<Boolean> updateCouponAmount(@PathVariable Long orderId, @PathVariable BigDecimal couponAmount);

    /**
     * 更新订单支付信息（已支付）
     *
     * @param orderNo
     * @return
     */
    @GetMapping("/order/info/getOrderPayVo/{orderNo}")
    public Result<Boolean> updateOrderPay(@PathVariable String orderNo);

    /**
     * 司机发送账单信息
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @GetMapping("/order/info/sendOrderBillInfo/{orderId}/{driverId}")
    Result<Boolean> sendOrderBillInfo(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);

    /**
     * 根据订单id获取实际分账信息
     *
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/getOrderProfitsharing/{orderId}")
    Result<OrderProfitsharingVo> getOrderProfitsharing(@PathVariable("orderId") Long orderId);

    /**
     * 根据订单id获取实际账单信息
     *
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/getOrderBillInfo/{orderId}")
    Result<OrderBillVo> getOrderBillInfo(@PathVariable("orderId") Long orderId);

    /**
     * 司机抢单
     *
     * @param driverId
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/robNewOrder/{driverId}/{orderId}")
    Result<Boolean> robNewOrder(@PathVariable("driverId") Long driverId, @PathVariable("orderId") Long orderId);

    /**
     * 保存订单信息
     *
     * @param orderInfoForm
     * @return
     */
    @PostMapping("/order/info/saveOrderInfo")
    Result<Long> saveOrderInfo(@RequestBody OrderInfoForm orderInfoForm);

    /**
     * 根据订单id获取订单状态
     *
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/getOrderStatus/{orderId}")
    Result<Integer> getOrderStatus(@PathVariable("orderId") Long orderId);

    /**
     * 乘客端查找当前订单
     *
     * @param customerId
     * @return
     */
    @GetMapping("/order/info/searchCustomerCurrentOrder/{customerId}")
    Result<CurrentOrderInfoVo> searchCustomerCurrentOrder(@PathVariable("customerId") Long customerId);


    /**
     * 司机端查找当前订单
     *
     * @param driverId
     * @return
     */
    @GetMapping("/order/info/searchDriverCurrentOrder/{driverId}")
    Result<CurrentOrderInfoVo> searchDriverCurrentOrder(@PathVariable("driverId") Long driverId);


    /**
     * 根据订单id获取订单信息
     *
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/getOrderInfo/{orderId}")
    Result<OrderInfo> getOrderInfo(@PathVariable("orderId") Long orderId);

    /**
     * 司机到达起始点
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @GetMapping("/order/info/driverArriveStartLocation/{orderId}/{driverId}")
    Result<Boolean> driverArriveStartLocation(@PathVariable("orderId") Long orderId, @PathVariable("driverId") Long driverId);


    /**
     * 更新代驾车辆信息 * @param updateOrderCartForm * @return
     */
    @PostMapping("/order/info/updateOrderCart")
    Result<Boolean> updateOrderCart(@RequestBody UpdateOrderCartForm updateOrderCartForm);

    /**
     * 开始代驾服务
     *
     * @param startDriveForm
     * @return
     */
    @PostMapping("/order/info/startDriver")
    Result<Boolean> startDrive(@RequestBody StartDriveForm startDriveForm);

    /**
     * 获取时间内订单数目
     *
     * @param startTime
     * @param endTime
     * @return
     */
    @GetMapping("/order/info/getOrderNumByTime/{startTime}/{endTime}")
    Result<Long> getOrderNumByTime(@PathVariable String startTime, @PathVariable String endTime);

    /**
     * 结束司机服务
     *
     * @param updateOrderBillForm
     * @return
     */
    @PostMapping("/order/info/endDriver")
    Result<Boolean> endDriver(@RequestBody UpdateOrderBillForm updateOrderBillForm);

    /**
     * 获取乘客订单分页列表
     *
     * @param customerId
     * @param page
     * @param limit
     * @return
     */
    @GetMapping("/order/info/findCustomerOrderPage/{customerId}/{page}/{limit}")
    Result<PageVo> findCustomerOrderPage(@PathVariable("customerId") Long customerId,
                                         @PathVariable("page") Long page,
                                         @PathVariable("limit") Long limit);

    /**
     * 获取司机订单分页列表
     *
     * @param driverId
     * @param page
     * @param limit
     * @return
     */
    @GetMapping("/order/info/findDriverOrderPage/{driverId}/{page}/{limit}")
    Result<PageVo> findDriverOrderPage(@PathVariable("driverId") Long driverId,
                                       @PathVariable("page") Long page,
                                       @PathVariable("limit") Long limit);

    /**
     * 用户取消订单，更新订单信息
     *
     * @param orderId
     * @return
     */
    @GetMapping("/order/info/customerCancelNoAcceptOrder/{orderId}")
    Result<Boolean> customerCancelNoAcceptOrder(@PathVariable Long orderId);

    /**
     * 获取订单支付信息
     *
     * @param orderNo
     * @param customerId
     * @return
     */
    @GetMapping("/order/info/getOrderPayVo/{orderNo}/{customerId}")
    Result<OrderPayVo> getOrderPayVo(@PathVariable("orderNo") String orderNo, @PathVariable("customerId") Long customerId);
}