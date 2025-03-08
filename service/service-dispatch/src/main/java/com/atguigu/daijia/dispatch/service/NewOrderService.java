package com.atguigu.daijia.dispatch.service;

import com.atguigu.daijia.model.vo.dispatch.NewOrderTaskVo;
import com.atguigu.daijia.model.vo.order.NewOrderDataVo;

import java.util.List;

public interface NewOrderService {

    Long addAndStartTask(NewOrderTaskVo newOrderTaskVo);

    Boolean executeTask(long jobId);

    List<NewOrderDataVo> findNewOrderQueueData(Long driverId);

    Boolean clearNewOrderQueueData(Long driverId);

    Boolean customerCancelNoAcceptOrder(Long orderId);
}
