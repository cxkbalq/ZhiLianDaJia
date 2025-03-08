package com.atguigu.daijia.order.service.impl;

import com.atguigu.daijia.common.execption.zdyException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.model.entity.order.OrderMonitor;
import com.atguigu.daijia.model.entity.order.OrderMonitorRecord;
import com.atguigu.daijia.order.mapper.OrderMonitorMapper;
import com.atguigu.daijia.order.repository.OrderMonitorRecordRepository;
import com.atguigu.daijia.order.service.OrderMonitorService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class OrderMonitorServiceImpl extends ServiceImpl<OrderMonitorMapper, OrderMonitor> implements OrderMonitorService {

    @Autowired
    private OrderMonitorRecordRepository orderMonitorRecordRepository;

    /***
     * 保存订单监控记录数据
     * @param orderMonitorRecord
     * @return
     */
    @Override
    public Boolean saveOrderMonitorRecord(OrderMonitorRecord orderMonitorRecord) {
        //保存到mongodb里面
        OrderMonitorRecord save = orderMonitorRecordRepository.save(orderMonitorRecord);
        //保存到mysql里
        OrderMonitor orderMonitor = new OrderMonitor();
        BeanUtils.copyProperties(orderMonitorRecord,orderMonitor);
        //测试数据
        orderMonitor.setAuditNum(9);
        orderMonitor.setFileNum(9);
        orderMonitor.setCreateTime(new Date());
        orderMonitor.setIsDeleted(1);
        boolean save1 = this.save(orderMonitor);
        if(save != null && save1){
            return true;
        }else {
            throw new zdyException(ResultCodeEnum.FAIL);
        }
    }
}
