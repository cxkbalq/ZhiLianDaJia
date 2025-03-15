package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderBill;
import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.entity.order.OrderProfitsharing;
import com.atguigu.daijia.model.entity.order.OrderStatusLog;
import com.atguigu.daijia.model.enums.OrderStatus;
import com.atguigu.daijia.model.form.order.OrderInfoForm;
import com.atguigu.daijia.model.form.order.StartDriveForm;
import com.atguigu.daijia.model.form.order.UpdateOrderBillForm;
import com.atguigu.daijia.model.form.order.UpdateOrderCartForm;
import com.atguigu.daijia.model.vo.base.PageVo;
import com.atguigu.daijia.model.vo.order.*;
import com.atguigu.daijia.order.mapper.OrderBillMapper;
import com.atguigu.daijia.order.mapper.OrderInfoMapper;
import com.atguigu.daijia.order.mapper.OrderProfitsharingMapper;
import com.atguigu.daijia.order.mapper.OrderStatusLogMapper;
import com.atguigu.daijia.order.service.OrderInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderStatusLogMapper orderStatusLogMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private OrderBillMapper orderBillMapper;
    @Autowired
    private OrderProfitsharingMapper orderProfitsharingMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 保存订单信息
     *
     * @return
     */
    @Override
    public Long saveOrderInfo(OrderInfoForm orderInfoForm) {
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoForm, orderInfo);
        String orderNo = UUID.randomUUID().toString().replaceAll("-", "");
        orderInfo.setStatus(OrderStatus.WAITING_ACCEPT.getStatus());
        orderInfo.setOrderNo(orderNo);
        //发送延时消息
        this.sendDelayMessage(orderNo);
        this.save(orderInfo);

        //创建redis标识，减少数据库压力
        redisTemplate.opsForValue().set(RedisConstant.ORDER_ACCEPT_MARK, orderNo, 16, TimeUnit.MINUTES);
        return orderInfo.getId();
    }

    /**
     * 发送延时消息
     *
     * @param orderNo
     */
    private void sendDelayMessage(String orderNo) {

        if (null == orderNo) {
            throw new zdyException(ResultCodeEnum.DATA_ERROR);
        }
        //确保投递成功，以及唯一性
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        //AMQP 2.1+：CorrelationData 的 getFuture() 返回的是 CompletableFuture，其回调方法为 whenComplete，而非旧版的 addCallback。
        correlationData.getFuture().whenComplete(((confirm, throwable) -> {
            if (throwable != null) {
                log.error("消息发送失败", throwable);
            } else {
                if (confirm.isAck()) {
                    log.info("消息投递成功, ID: {}", correlationData.getId());

                } else {
                    log.error("消息投递失败, ID: {}", correlationData.getId());
                }
            }
        }));
        rabbitTemplate.convertAndSend("daijia.delay.direct", "qx", orderNo, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                //15发送消息
                message.getMessageProperties().setDelay(60000 * 15);
                return message;
            }
        }, correlationData);
        log.info("rabbit发送成功: 延时检测支付状态");
    }

    /**
     * 获取订单状态
     *
     * @param orderId
     * @return
     */

    @Override
    public Integer getOrderStatus(Long orderId) {
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderInfo::getId, orderId);
        lambdaQueryWrapper.select(OrderInfo::getStatus);
        OrderInfo one = this.getOne(lambdaQueryWrapper);
        if (null == one) {
            //返回null，feign解析会抛出异常，给默认值，后续会用
            return OrderStatus.NULL_ORDER.getStatus();
        }
        return one.getStatus();
    }

    /**
     * 司机抢单
     *
     * @param driverId
     * @param orderId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean robNewOrder(Long driverId, Long orderId) {

        //抢单成功或取消订单，都会删除该key，redis判断，减少数据库压力
        if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK))) {
            //抢单失败
            throw new zdyException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //对数据进行加锁
        // 初始化分布式锁，创建一个RLock实例
        // 抢新订单锁
        // public static final String ROB_NEW_ORDER_LOCK = "rob:new:order:lock";
        RLock lock = redissonClient.getLock(RedisConstant.ROB_NEW_ORDER_LOCK + orderId);


        try {
            /**
             * TryLock是一种非阻塞式的分布式锁，实现原理：Redis的SETNX命令
             * 参数：
             *     waitTime：等待获取锁的时间
             *     leaseTime：加锁的时间
             */
            boolean flag = lock.tryLock(RedisConstant.ROB_NEW_ORDER_LOCK_WAIT_TIME, RedisConstant.ROB_NEW_ORDER_LOCK_LEASE_TIME,
                    TimeUnit.SECONDS);
            //获取到锁
            if (flag) {
                //二次判断，防止重复抢单
                if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisConstant.ORDER_ACCEPT_MARK))) {
                    //抢单失败
                    throw new zdyException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                //修改订单状态
                //update order_info set status = 2, driver_id = #{driverId} where id = #{id}
                //修改字段
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setId(orderId);
                orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
                orderInfo.setAcceptTime(new Date());
                orderInfo.setDriverId(driverId);
                int rows = orderInfoMapper.updateById(orderInfo);
                if (rows != 1) {
                    //抢单失败
                    throw new zdyException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
                }

                //记录日志
                this.log(orderId, orderInfo.getStatus());

                //删除redis订单标识
                redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
            }

        } catch (Exception e) {
            //抢单失败
            throw new zdyException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        } finally {
            //检查是否有锁
            boolean locked = lock.isLocked();
            if (locked) {
                //释放锁
                lock.unlock();
            }
        }
        //修改当前信息
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        //ACCEPTED =2
        orderInfo.setStatus(OrderStatus.ACCEPTED.getStatus());
        orderInfo.setAcceptTime(new Date());
        orderInfo.setDriverId(driverId);

        //返回值是一个影响行数
        int rows = orderInfoMapper.updateById(orderInfo);
        if (rows != 1) {
            log.error("抢单失败！");
            // COB_NEW_ORDER_FAIL( 217, "抢单失败"),
            throw new zdyException(ResultCodeEnum.COB_NEW_ORDER_FAIL);
        }

        //记录日志
        this.log(orderId, orderInfo.getStatus());

        //删除redis订单标识
        redisTemplate.delete(RedisConstant.ORDER_ACCEPT_MARK);
        return true;
    }

    /**
     * 查找司机端订单信息
     *
     * @param driverId
     * @return
     */
    @Override
    public CurrentOrderInfoVo searchDriverCurrentOrder(Long driverId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //司机发送完账单，司机端主要流程就走完（当前这些节点，司机端会调整到相应的页面处理逻辑）
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus()
        };
        queryWrapper.in(OrderInfo::getStatus, statusArray);
        queryWrapper.orderByDesc(OrderInfo::getId);
        queryWrapper.last("limit 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if (null != orderInfo) {
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    /**
     * 查找用户端的订单信息
     *
     * @param customerId
     * @return
     */
    @Override
    public CurrentOrderInfoVo searchCustomerCurrentOrder(Long customerId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getCustomerId, customerId);
        //乘客端支付完订单，乘客端主要流程就走完（当前这些节点，乘客端会调整到相应的页面处理逻辑）
        Integer[] statusArray = {
                OrderStatus.ACCEPTED.getStatus(),
                OrderStatus.DRIVER_ARRIVED.getStatus(),
                OrderStatus.UPDATE_CART_INFO.getStatus(),
                OrderStatus.START_SERVICE.getStatus(),
                OrderStatus.END_SERVICE.getStatus(),
                OrderStatus.UNPAID.getStatus()
        };
        queryWrapper.in(OrderInfo::getStatus, statusArray);
        queryWrapper.orderByDesc(OrderInfo::getId);
        queryWrapper.last("limit 1");
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);
        CurrentOrderInfoVo currentOrderInfoVo = new CurrentOrderInfoVo();
        if (null != orderInfo) {
            currentOrderInfoVo.setStatus(orderInfo.getStatus());
            currentOrderInfoVo.setOrderId(orderInfo.getId());
            currentOrderInfoVo.setIsHasCurrentOrder(true);
        } else {
            currentOrderInfoVo.setIsHasCurrentOrder(false);
        }
        return currentOrderInfoVo;
    }

    /**
     * 司机到达起始位置
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean driverArriveStartLocation(Long orderId, Long driverId) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);

        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.DRIVER_ARRIVED.getStatus());
        updateOrderInfo.setArriveTime(new Date());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(orderId, OrderStatus.DRIVER_ARRIVED.getStatus());
        } else {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    /**
     * 更新时间订单状态
     *
     * @param updateOrderCartForm
     * @return
     */
    @Override
    public Boolean updateOrderCart(UpdateOrderCartForm updateOrderCartForm) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, updateOrderCartForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderCartForm.getDriverId());

        OrderInfo updateOrderInfo = new OrderInfo();
        BeanUtils.copyProperties(updateOrderCartForm, updateOrderInfo);
        updateOrderInfo.setStatus(OrderStatus.UPDATE_CART_INFO.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(updateOrderCartForm.getOrderId(), OrderStatus.UPDATE_CART_INFO.getStatus());
        } else {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    /***
     * 开始代驾服务
     * @param startDriveForm
     * @return
     */
    @Override
    public Boolean startDriver(StartDriveForm startDriveForm) {
        //根据订单id  +  司机id  更新订单状态 和 开始代驾时间
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getId, startDriveForm.getOrderId());
        wrapper.eq(OrderInfo::getDriverId, startDriveForm.getDriverId());

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(OrderStatus.START_SERVICE.getStatus());
        orderInfo.setStartServiceTime(new Date());
        orderInfo.setUpdateTime(new Date());
        //更新成功返回1
        int rows = orderInfoMapper.update(orderInfo, wrapper);
        if (rows == 1) {
            return true;
        } else {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
    }

    /**
     * 根据时间段获取总订单
     *
     * @param startTime
     * @param endTime
     * @return
     */
    @Override
    public Long getOrderNumByTime(String startTime, String endTime) {
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.ge(OrderInfo::getStartServiceTime, startTime);
        lambdaQueryWrapper.lt(OrderInfo::getEndServiceTime, endTime);
        Long l = orderInfoMapper.selectCount(lambdaQueryWrapper);
        return l;
    }

    /**
     * 结束服务
     *
     * @param updateOrderBillForm
     * @return
     */
    @Override
    @Transactional
    public Boolean endDriver(UpdateOrderBillForm updateOrderBillForm) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, updateOrderBillForm.getOrderId());
        queryWrapper.eq(OrderInfo::getDriverId, updateOrderBillForm.getDriverId());
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.END_SERVICE.getStatus());
        updateOrderInfo.setRealAmount(updateOrderBillForm.getTotalAmount());
        updateOrderInfo.setFavourFee(updateOrderBillForm.getFavourFee());
        updateOrderInfo.setEndServiceTime(new Date());
        updateOrderInfo.setRealDistance(updateOrderBillForm.getRealDistance());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(updateOrderBillForm.getOrderId(), OrderStatus.END_SERVICE.getStatus());

            //插入实际账单数据
            OrderBill orderBill = new OrderBill();
            BeanUtils.copyProperties(updateOrderBillForm, orderBill);
            orderBill.setOrderId(updateOrderBillForm.getOrderId());
            orderBill.setPayAmount(orderBill.getTotalAmount());
            orderBillMapper.insert(orderBill);

            //插入分账信息数据
            OrderProfitsharing orderProfitsharing = new OrderProfitsharing();
            BeanUtils.copyProperties(updateOrderBillForm, orderProfitsharing);
            orderProfitsharing.setOrderId(updateOrderBillForm.getOrderId());
            //这个地方理论上是后台可以控制对应的规则文件达到不同效果，从数据库查询，这里为了测试，规则选用id默认为1
            //这个地方理论上是后台可以控制对应的规则文件达到不同效果，从数据库查询，这里为了测试，规则选用id默认为1
            //这个地方理论上是后台可以控制对应的规则文件达到不同效果，从数据库查询，这里为了测试，规则选用id默认为1,
            //orderProfitsharing.setRuleId(updateOrderBillForm.getProfitsharingRuleId());
            orderProfitsharing.setRuleId(1l);
            orderProfitsharing.setStatus(2);

//            BigDecimal bigDecimal = updateOrderBillForm.getTotalAmount().subtract(updateOrderBillForm.getPaymentFee());
//            //如果有司机费率，那么要减去
//            if (updateOrderBillForm.getDriverTaxFee() != null) {
//                bigDecimal.subtract(updateOrderBillForm.getDriverTaxFee());
//            }
            orderProfitsharing.setDriverIncome(updateOrderBillForm.getDriverIncome());
            orderProfitsharing.setOrderAmount(updateOrderBillForm.getOrderAmount());
            //平台费率
            orderProfitsharing.setPaymentFee(updateOrderBillForm.getPaymentFee());
            orderProfitsharing.setPaymentRate(updateOrderBillForm.getPaymentRate());
            orderProfitsharing.setCreateTime(new Date());
            orderProfitsharing.setUpdateTime(new Date());
            orderProfitsharing.setDriverTaxRate(updateOrderBillForm.getDriverTaxRate());
            orderProfitsharing.setDriverTaxFee(updateOrderBillForm.getDriverTaxFee());
            orderProfitsharingMapper.insert(orderProfitsharing);
        } else {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    /**
     * 查询用户的订单信息
     *
     * @param pageParam
     * @param customerId
     * @return
     */
    @Override
    public PageVo findCustomerOrderPage(Page<OrderInfo> pageParam, Long customerId) {
        IPage<OrderListVo> orderListVoIPage = orderInfoMapper.selectCustomerOrderPage(pageParam, customerId);
        return new PageVo(orderListVoIPage.getRecords(), orderListVoIPage.getPages(), orderListVoIPage.getTotal());
    }

    /**
     * 查询司机的订单信息
     *
     * @param pageParam
     * @param driverId
     * @return
     */
    @Override
    public PageVo findDriverOrderPage(Page<OrderInfo> pageParam, Long driverId) {
        IPage<OrderListVo> pageInfo = orderInfoMapper.selectDriverOrderPage(pageParam, driverId);
        return new PageVo(pageInfo.getRecords(), pageInfo.getPages(), pageInfo.getTotal());
    }


    /**
     * 用户自动取消订单
     *
     * @param orderid
     * @return
     */
    @Override
    public Boolean customerCancelNoAcceptOrder(String orderid) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(9); // 用户取消
        orderInfo.setUpdateTime(new Date());
        LambdaUpdateWrapper<OrderInfo> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(OrderInfo::getOrderNo, orderid);
        int i = orderInfoMapper.update(orderInfo, lambdaUpdateWrapper);
        return i == 1 ? true : false;
    }

    /**
     * 用户主动取消订单
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean customerCancelNoAcceptOrder2(Long orderNo) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setStatus(9); // 用户取消
        orderInfo.setUpdateTime(new Date());
        LambdaUpdateWrapper<OrderInfo> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(OrderInfo::getOrderNo, orderNo);
        int i = orderInfoMapper.update(orderInfo, lambdaUpdateWrapper);
        return i == 1 ? true : false;
    }

    /**
     * 根据订单id获取实际账单信息
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderBillVo getOrderBillInfo(Long orderId) {
        OrderBill orderBill = orderBillMapper.selectOne(new LambdaQueryWrapper<OrderBill>().eq(OrderBill::getOrderId, orderId));
        OrderBillVo orderBillVo = new OrderBillVo();
        BeanUtils.copyProperties(orderBill, orderBillVo);
        return orderBillVo;
    }

    /**
     * 根据订单id获取实际分账信息
     *
     * @param orderId
     * @return
     */
    @Override
    public OrderProfitsharingVo getOrderProfitsharing(Long orderId) {
        OrderProfitsharing orderProfitsharing = orderProfitsharingMapper.selectOne(new LambdaQueryWrapper<OrderProfitsharing>().eq(OrderProfitsharing::getOrderId, orderId));
        OrderProfitsharingVo orderProfitsharingVo = new OrderProfitsharingVo();
        BeanUtils.copyProperties(orderProfitsharing, orderProfitsharingVo);
        return orderProfitsharingVo;
    }

    /**
     * 发送账单信息
     *
     * @param orderId
     * @param driverId
     * @return
     */
    @Override
    public Boolean sendOrderBillInfo(Long orderId, Long driverId) {
        //更新订单信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getId, orderId);
        queryWrapper.eq(OrderInfo::getDriverId, driverId);
        //更新字段
        OrderInfo updateOrderInfo = new OrderInfo();
        updateOrderInfo.setStatus(OrderStatus.UNPAID.getStatus());
        //只能更新自己的订单
        int row = orderInfoMapper.update(updateOrderInfo, queryWrapper);
        if (row == 1) {
            //记录日志
            this.log(orderId, OrderStatus.UNPAID.getStatus());
        } else {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    /**
     * 获取订单支付信息
     *
     * @param orderNo
     * @param customerId
     * @return
     */
    @Override
    public OrderPayVo getOrderPayVo(String orderNo, Long customerId) {
        OrderPayVo orderPayVo = orderInfoMapper.selectOrderPayVo(orderNo, customerId);
        if (null != orderPayVo) {
            String content = orderPayVo.getStartLocation() + " 到 " + orderPayVo.getEndLocation();
            orderPayVo.setContent(content);
        }
        return orderPayVo;
    }

    /**
     * 更新订单支付信息（已支付）
     *
     * @param orderNo
     * @return
     */
    @Override
    public Boolean updateOrderPay(String orderNo) {
        LambdaUpdateWrapper<OrderInfo> orderInfoLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        orderInfoLambdaUpdateWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setUpdateTime(new Date());
        orderInfo.setStatus(8);
        return this.update(orderInfo, orderInfoLambdaUpdateWrapper);
    }

    /**
     * 更新订单优惠券金额
     *
     * @param orderId
     * @param couponAmount
     * @return
     */
    @Override
    public Boolean updateCouponAmount(Long orderId, BigDecimal couponAmount) {
        int row = orderBillMapper.updateCouponAmount(orderId, couponAmount);
        if (row != 1) {
            throw new zdyException(ResultCodeEnum.UPDATE_ERROR);
        }
        return true;
    }

    /**
     * 插入日志
     *
     * @param orderId
     * @param status
     */
    public void log(Long orderId, Integer status) {
        OrderStatusLog orderStatusLog = new OrderStatusLog();
        orderStatusLog.setOrderId(orderId);
        orderStatusLog.setOrderStatus(status);
        orderStatusLog.setOperateTime(new Date());
        orderStatusLogMapper.insert(orderStatusLog);
    }
}
