package com.atguigu.daijia.driver.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.daijia.common.constant.SystemConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.LocationUtil;
import com.atguigu.daijia.dispatch.client.NewOrderFeignClient;
import com.atguigu.daijia.driver.service.OrderService;
import com.atguigu.daijia.map.client.LocationFeignClient;
import com.atguigu.daijia.map.client.MapFeignClient;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.map.CalculateDrivingLineForm;
import com.atguigu.daijia.model.form.order.OrderFeeForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.form.rules.FeeRuleRequestForm;
import com.atguigu.daijia.model.form.rules.ProfitsharingRuleRequestForm;
import com.atguigu.daijia.model.form.rules.RewardRuleRequestForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.map.DrivingLineVo;
import com.atguigu.daijia.model.vo.map.OrderLocationVo;
import com.atguigu.daijia.model.vo.map.OrderServiceLastLocationVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.model.vo.rules.FeeRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.ProfitsharingRuleResponseVo;
import com.atguigu.daijia.model.vo.rules.RewardRuleResponseVo;
import com.atguigu.daijia.order.client.OrderInfoFeignClient;
import com.atguigu.daijia.rules.client.FeeRuleFeignClient;
import com.atguigu.daijia.rules.client.ProfitsharingRuleFeignClient;
import com.atguigu.daijia.rules.client.RewardRuleFeignClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;
    @Autowired
    private NewOrderFeignClient newOrderFeignClient;
    @Autowired
    private MapFeignClient mapFeignClient;
    @Autowired
    private LocationFeignClient locationFeignClient;
    @Autowired
    private FeeRuleFeignClient feeRuleFeignClient;
    @Autowired
    private RewardRuleFeignClient rewardRuleFeignClient;
    @Autowired
    private ProfitsharingRuleFeignClient profitsharingRuleFeignClient;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;


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
     * 获取新的订单
     *
     * @param driverId
     * @return
     */
    @Override
    public List<NewOrderDataVo> findNewOrderQueueData(Long driverId) {
        return newOrderFeignClient.findNewOrderQueueData(driverId).getData();
    }

    /**
     * 查找司机端订单
     *
     * @param driverId
     * @return
     */
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        return orderInfoFeignClient.searchDriverCurrentOrder(driverId).getData();
    }


    /**
     * 获取订单相关信息
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @Override
    public OrderInfoVo getOrderInfo(Long orderId, Long driverId) {
        //订单信息
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();
        if (orderInfo.getDriverId().longValue() != driverId.longValue()) {
            throw new zdyException(ResultCodeEnum.ILLEGAL_REQUEST);
        }

        //账单信息
        OrderBillVo orderBillVo = null;
        //分账信息
        OrderProfitsharingVo orderProfitsharing = null;
        if (orderInfo.getStatus().intValue() >= OrderStatus.END_SERVICE.getStatus().intValue()) {
            orderBillVo = orderInfoFeignClient.getOrderBillInfo(orderId).getData();
            //获取分账信息
            orderProfitsharing = orderInfoFeignClient.getOrderProfitsharing(orderId).getData();
        }
        //封装订单信息
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        orderInfoVo.setOrderId(orderId);
        BeanUtils.copyProperties(orderInfo, orderInfoVo);
        orderInfoVo.setOrderBillVo(orderBillVo);
        orderInfoVo.setOrderProfitsharingVo(orderProfitsharing);
        return orderInfoVo;
    }

    /**
     * 计算最佳驾驶线路，司机到接单地方
     *
     * @param calculateDrivingLineForm
     * @return
     */

    @Override
    public DrivingLineVo calculateDrivingLine(CalculateDrivingLineForm calculateDrivingLineForm) {
        return mapFeignClient.calculateDrivingLine(calculateDrivingLineForm).getData();
    }

    /**
     * 到达地点，更新订单状态司机端
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @Override
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        // orderInfo有代驾开始位置
        OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderId).getData();

        //司机当前位置
        OrderLocationVo orderLocationVo = locationFeignClient.getCacheOrderLocation(orderId).getData();

        //司机当前位置 和 代驾开始位置距离
        double distance = LocationUtil.getDistance(orderInfo.getStartPointLatitude().doubleValue(),
                orderInfo.getStartPointLongitude().doubleValue(),
                orderLocationVo.getLatitude().doubleValue(),
                orderLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_START_LOCATION_DISTION) {
            throw new zdyException(ResultCodeEnum.DRIVER_START_LOCATION_DISTION_ERROR);
        }

        return orderInfoFeignClient.driverArriveStartLocation(orderId, driverId).getData();
    }

    /**
     * 到达地点，更新车辆相关信息
     *
     * @param updateOrderCartForm
     * @return
     */
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        return orderInfoFeignClient.updateOrderCart(updateOrderCartForm).getData();
    }

    /**
     * 司机抢单
     *
     * @param driverId
     * @param orderId
     * @return
     */
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {
        return orderInfoFeignClient.robNewOrder(driverId, orderId).getData();
    }

    /**
     * 司机开启代驾
     *
     * @param startDriveForm
     * @return
     */
    @Override
    public Boolean startDrive(StartDriveForm startDriveForm) {
        return orderInfoFeignClient.startDrive(startDriveForm).getData();
    }

    /**
     * 结束代驾
     *
     * @param orderFeeForm
     * @return
     */
    @SneakyThrows
    @Override
    public Boolean endDrive(OrderFeeForm orderFeeForm) {
        //1 根据orderId获取订单信息，判断当前订单是否司机接单
        CompletableFuture<OrderInfo> orderInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            OrderInfo orderInfo = orderInfoFeignClient.getOrderInfo(orderFeeForm.getOrderId()).getData();
            if (orderInfo.getDriverId() != orderFeeForm.getDriverId()) {
                throw new zdyException(ResultCodeEnum.ILLEGAL_REQUEST);
            }
            return orderInfo;
        }, threadPoolExecutor);

        //2.对司机是否到达进行判断
        CompletableFuture<OrderServiceLastLocationVo> orderServiceLastLocationVoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            OrderServiceLastLocationVo orderServiceLastLocationVo = locationFeignClient.
                    getOrderServiceLastLocation(orderFeeForm.getOrderId()).getData();
            return orderServiceLastLocationVo;
        }, threadPoolExecutor);

        //3.等待2.3任务完成，计算司机当前位置 距离 结束代驾位置
        CompletableFuture.allOf(orderInfoCompletableFuture, orderServiceLastLocationVoCompletableFuture).join();
        //获取相关信息
        OrderInfo orderInfo = orderInfoCompletableFuture.get();
        OrderServiceLastLocationVo orderServiceLastLocationVo = orderServiceLastLocationVoCompletableFuture.get();
        //司机当前位置 距离 结束代驾位置
        double distance = LocationUtil.getDistance(orderInfo.getEndPointLatitude().doubleValue(),
                orderInfo.getEndPointLongitude().doubleValue(),
                orderServiceLastLocationVo.getLatitude().doubleValue(),
                orderServiceLastLocationVo.getLongitude().doubleValue());
        if (distance > SystemConstant.DRIVER_END_LOCATION_DISTION) {
            throw new zdyException(ResultCodeEnum.DRIVER_END_LOCATION_DISTION_ERROR);
        }

        //4 计算订单实际里程
        CompletableFuture<BigDecimal> bigDecimalCompletableFuture = CompletableFuture.supplyAsync(() -> {
            BigDecimal realDistance =
                    locationFeignClient.calculateOrderRealDistance(orderFeeForm.getOrderId()).getData();
            return realDistance;
        }, threadPoolExecutor);

        //5计算代驾实际费用
        CompletableFuture<FeeRuleResponseVo> feeRuleResponseVoCompletableFuture = bigDecimalCompletableFuture.thenApplyAsync((realDistance) -> {
            //远程调用，计算代驾费用
            //封装FeeRuleRequestForm
            FeeRuleRequestForm feeRuleRequestForm = new FeeRuleRequestForm();
            feeRuleRequestForm.setDistance(realDistance);
            feeRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());

            //计算司机到达代驾开始位置时间
            //orderInfo.getArriveTime() - orderInfo.getAcceptTime()
            // 分钟 = 毫秒 / 1000 * 60
            Integer waitMinute =
                    Math.abs((int) ((orderInfo.getArriveTime().getTime() - orderInfo.getAcceptTime().getTime()) / (1000 * 60)));
            feeRuleRequestForm.setWaitMinute(waitMinute);
            //远程调用 代驾费用
            FeeRuleResponseVo feeRuleResponseVo = feeRuleFeignClient.calculateOrderFee(feeRuleRequestForm).getData();
            //实际费用 = 代驾费用 + 其他费用（停车费）
            BigDecimal totalAmount =
                    feeRuleResponseVo.getTotalAmount().add(orderFeeForm.getTollFee())
                            .add(orderFeeForm.getParkingFee())
                            .add(orderFeeForm.getOtherFee())
                            .add(orderInfo.getFavourFee());
            feeRuleResponseVo.setTotalAmount(totalAmount);
            return feeRuleResponseVo;
        }, threadPoolExecutor);

        //6 计算系统奖励
        CompletableFuture<Long> longCompletableFuture = CompletableFuture.supplyAsync(() -> {
            String startTime = new DateTime(orderInfo.getStartServiceTime()).toString("yyyy-MM-dd") + " 00:00:00";
            String endTime = new DateTime().toString("yyyy-MM-dd") + " 23:59:59";
            Long orderNum = orderInfoFeignClient.getOrderNumByTime(startTime, endTime).getData();
            return orderNum;
        }, threadPoolExecutor);
        //封装参数
        CompletableFuture<RewardRuleResponseVo> rewardRuleResponseVoCompletableFuture = longCompletableFuture.thenApplyAsync((orderNum) -> {
            RewardRuleRequestForm rewardRuleRequestForm = new RewardRuleRequestForm();
            rewardRuleRequestForm.setStartTime(orderInfo.getStartServiceTime());
            rewardRuleRequestForm.setOrderNum(orderNum);
            RewardRuleResponseVo rewardRuleResponseVo = rewardRuleFeignClient.calculateOrderRewardFee(rewardRuleRequestForm).getData();
            return rewardRuleResponseVo;
        }, threadPoolExecutor);


        //7计算分账信息  thenCombineAsync在两个独立的异步任务完成后，将它们的返回值合并处理并生成一个新的结果
        CompletableFuture<ProfitsharingRuleResponseVo> profitsharingRuleResponseVoCompletableFuture = feeRuleResponseVoCompletableFuture
                .thenCombineAsync(longCompletableFuture, (feeRuleResponseVo, orderNum) -> {
                    ProfitsharingRuleRequestForm profitsharingRuleRequestForm = new ProfitsharingRuleRequestForm();
                    profitsharingRuleRequestForm.setOrderAmount(feeRuleResponseVo.getTotalAmount());
                    profitsharingRuleRequestForm.setOrderNum(orderNum);
                    ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleFeignClient.calculateOrderProfitsharingFee(profitsharingRuleRequestForm).getData();
                    log.info("结束代驾，分账信息：{}", JSON.toJSONString(profitsharingRuleResponseVo));
                    return profitsharingRuleResponseVo;
                });

        //等待完成
        CompletableFuture.allOf(orderInfoCompletableFuture,
                bigDecimalCompletableFuture, //实际距离
                feeRuleResponseVoCompletableFuture,
                longCompletableFuture,
                rewardRuleResponseVoCompletableFuture,
                profitsharingRuleResponseVoCompletableFuture
        ).join();
        //获取执行结果
        BigDecimal realDistance = bigDecimalCompletableFuture.get();
        FeeRuleResponseVo feeRuleResponseVo = feeRuleResponseVoCompletableFuture.get();
        RewardRuleResponseVo rewardRuleResponseVo = rewardRuleResponseVoCompletableFuture.get();
        ProfitsharingRuleResponseVo profitsharingRuleResponseVo = profitsharingRuleResponseVoCompletableFuture.get();

        //6 封装实体类，结束代驾更新订单，添加账单和分账信息
        UpdateOrderBillForm updateOrderBillForm = new UpdateOrderBillForm();
        updateOrderBillForm.setOrderId(orderFeeForm.getOrderId());
        updateOrderBillForm.setDriverId(orderFeeForm.getDriverId());
        //路桥费、停车费、其他费用
        updateOrderBillForm.setTollFee(orderFeeForm.getTollFee());
        updateOrderBillForm.setParkingFee(orderFeeForm.getParkingFee());
        updateOrderBillForm.setOtherFee(orderFeeForm.getOtherFee());
        //乘客好处费
        updateOrderBillForm.setFavourFee(orderInfo.getFavourFee());

        //实际里程
        updateOrderBillForm.setRealDistance(realDistance);
        //订单奖励信息
        BeanUtils.copyProperties(rewardRuleResponseVo, updateOrderBillForm);
        //代驾费用信息
        BeanUtils.copyProperties(feeRuleResponseVo, updateOrderBillForm);
        //分账相关信息
        BeanUtils.copyProperties(profitsharingRuleResponseVo, updateOrderBillForm);
        updateOrderBillForm.setProfitsharingRuleId(profitsharingRuleResponseVo.getProfitsharingRuleId());
        log.info("当前相关信息updateOrderBillForm：" + updateOrderBillForm.toString());
        orderInfoFeignClient.endDriver(updateOrderBillForm);

        return true;
    }

    /**
     * 获取司机订单分页列表
     *
     * @param driverId
     * @param page
     * @param limit
     * @return
     */
    @Override
    public PageVo findDriverOrderPage(Long driverId, Long page, Long limit) {
        return orderInfoFeignClient.findDriverOrderPage(driverId, page, limit).getData();
    }

    /**
     *司机发送账单信息
     * @param orderId
     * @param driverId
     * @return
     */
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        return orderInfoFeignClient.sendOrderBillInfo(orderId, driverId).getData();
    }
}
