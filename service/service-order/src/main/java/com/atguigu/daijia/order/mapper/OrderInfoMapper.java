package com.atguigu.daijia.order.mapper;

import com.atguigu.daijia.model.entity.order.OrderInfo;
import com.atguigu.daijia.model.vo.order.OrderListVo;
import com.atguigu.daijia.model.vo.order.OrderPayVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
     IPage<OrderListVo> selectCustomerOrderPage(Page<OrderInfo> page, @Param("customerId") Long customerId);

    IPage<OrderListVo> selectDriverOrderPage(Page<OrderInfo> pageParam, Long driverId);

    OrderPayVo selectOrderPayVo(String orderNo, Long customerId);
}
