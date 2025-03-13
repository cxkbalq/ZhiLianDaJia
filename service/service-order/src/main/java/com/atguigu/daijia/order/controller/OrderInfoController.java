package com.atguigu.daijia.order.controller;

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
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Tag(name = "订单API接口管理")
@RestController
@Slf4j
@RequestMapping(value = "/order/info")
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoController {
    @Autowired
    private OrderInfoService orderInfoService;

    @Operation(summary = "更新订单支付信息（已支付）")
    @GetMapping("/getOrderPayVo/{orderNo}")
    public Result<Boolean> updateOrderPay(@PathVariable String orderNo) {
        return Result.ok(orderInfoService.updateOrderPay(orderNo));
    }
    @Operation(summary = "获取订单支付信息")
    @GetMapping("/getOrderPayVo/{orderNo}/{customerId}")
    public Result<OrderPayVo> getOrderPayVo(@PathVariable String orderNo, @PathVariable Long customerId) {
        return Result.ok(orderInfoService.getOrderPayVo(orderNo, customerId));
    }

    @Operation(summary = "发送账单信息")
    @GetMapping("/sendOrderBillInfo/{orderId}/{driverId}")
    Result<Boolean> sendOrderBillInfo(@PathVariable Long orderId, @PathVariable Long driverId) {
        return Result.ok(orderInfoService.sendOrderBillInfo(orderId, driverId));
    }

    @Operation(summary = "根据订单id获取实际分账信息")
    @GetMapping("/getOrderProfitsharing/{orderId}")
    public Result<OrderProfitsharingVo> getOrderProfitsharing(@PathVariable Long orderId) {
        return Result.ok(orderInfoService.getOrderProfitsharing(orderId));
    }

    @Operation(summary = "根据订单id获取实际账单信息")
    @GetMapping("/getOrderBillInfo/{orderId}")
    public Result<OrderBillVo> getOrderBillInfo(@PathVariable Long orderId) {
        return Result.ok(orderInfoService.getOrderBillInfo(orderId));
    }

    @Operation(summary = "根据时间段获取订单数")
    @GetMapping("/getOrderNumByTime/{startTime}/{endTime}")
    public Result<Long> getOrderNumByTime(@PathVariable String startTime, @PathVariable String endTime) {
        return Result.ok(orderInfoService.getOrderNumByTime(startTime, endTime));
    }

    @Operation(summary = "保存订单信息")
    @PostMapping("/saveOrderInfo")
    public Result<Long> saveOrderInfo(@RequestBody OrderInfoForm orderInfoForm) {
        return Result.ok(orderInfoService.saveOrderInfo(orderInfoForm));
    }

    @Operation(summary = "根据订单id获取订单状态")
    @GetMapping("/getOrderStatus/{orderId}")
    public Result<Integer> getOrderStatus(@PathVariable Long orderId) {
        return Result.ok(orderInfoService.getOrderStatus(orderId));
    }

    @Operation(summary = "司机抢单")
    @GetMapping("/robNewOrder/{driverId}/{orderId}")
    public Result<Boolean> robNewOrder(@PathVariable Long driverId, @PathVariable Long orderId) {
        return Result.ok(orderInfoService.robNewOrder(driverId, orderId));
    }

    @Operation(summary = "乘客端查找当前订单")
    @GetMapping("/searchCustomerCurrentOrder/{customerId}")
    public Result<CurrentOrderInfoVo> searchCustomerCurrentOrder(@PathVariable Long customerId) {
        return Result.ok(orderInfoService.searchCustomerCurrentOrder(customerId));
    }

    @Operation(summary = "司机端查找当前订单")
    @GetMapping("/searchDriverCurrentOrder/{driverId}")
    public Result<CurrentOrderInfoVo> searchDriverCurrentOrder(@PathVariable Long driverId) {
        return Result.ok(orderInfoService.searchDriverCurrentOrder(driverId));
    }

    @Operation(summary = "根据订单id获取订单信息")
    @GetMapping("/getOrderInfo/{orderId}")
    public Result<OrderInfo> getOrderInfo(@PathVariable Long orderId) {
        return Result.ok(orderInfoService.getById(orderId));
    }

    @Operation(summary = "司机到达起始点")
    @GetMapping("/driverArriveStartLocation/{orderId}/{driverId}")
    public Result<Boolean> driverArriveStartLocation(@PathVariable Long orderId, @PathVariable Long driverId) {
        return Result.ok(orderInfoService.driverArriveStartLocation(orderId, driverId));
    }

    @Operation(summary = "更新代驾车辆信息")
    @PostMapping("/updateOrderCart")
    public Result<Boolean> updateOrderCart(@RequestBody UpdateOrderCartForm updateOrderCartForm) {
        return Result.ok(orderInfoService.updateOrderCart(updateOrderCartForm));
    }

    @Operation(summary = "开启代驾服务")
    @PostMapping("/startDriver")
    public Result<Boolean> startDriver(@RequestBody StartDriveForm startDriveForm) {
        Boolean flag = orderInfoService.startDriver(startDriveForm);
        return Result.ok(flag);
    }

    @Operation(summary = "结束代驾服务")
    @PostMapping("/endDriver")
    public Result<Boolean> endDriver(@RequestBody UpdateOrderBillForm updateOrderBillForm) {
        Boolean flag = orderInfoService.endDriver(updateOrderBillForm);
        return Result.ok(flag);
    }

    /**
     * 乘客端取消订单
     *
     * @param orderId
     * @return
     */
    @Operation(summary = "乘客端取消订单")
    @GetMapping("/customerCancelNoAcceptOrder/{orderId}")
    public Result<Boolean> customerCancelNoAcceptOrder(@PathVariable Long orderId) {
        log.info("乘客端取消订单");
        Boolean b = orderInfoService.customerCancelNoAcceptOrder(orderId);
        return Result.ok(b);

    }


    @Operation(summary = "获取乘客订单分页列表")
    @GetMapping("/findCustomerOrderPage/{customerId}/{page}/{limit}")
    public Result<PageVo> findCustomerOrderPage(
            @Parameter(name = "customerId", description = "乘客id", required = true)
            @PathVariable Long customerId,

            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,

            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<OrderInfo> pageParam = new Page<>(page, limit);
        PageVo pageVo = orderInfoService.findCustomerOrderPage(pageParam, customerId);
        pageVo.setPage(page);
        pageVo.setLimit(limit);
        return Result.ok(pageVo);
    }

    @Operation(summary = "获取司机订单分页列表")
    @GetMapping("/findDriverOrderPage/{driverId}/{page}/{limit}")
    public Result<PageVo> findDriverOrderPage(
            @Parameter(name = "driverId", description = "司机id", required = true)
            @PathVariable Long driverId,

            @Parameter(name = "page", description = "当前页码", required = true)
            @PathVariable Long page,

            @Parameter(name = "limit", description = "每页记录数", required = true)
            @PathVariable Long limit) {
        Page<OrderInfo> pageParam = new Page<>(page, limit);
        PageVo pageVo = orderInfoService.findDriverOrderPage(pageParam, driverId);
        pageVo.setPage(page);
        pageVo.setLimit(limit);
        return Result.ok(pageVo);
    }
}

